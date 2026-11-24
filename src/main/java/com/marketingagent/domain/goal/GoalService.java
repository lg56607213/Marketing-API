package com.marketingagent.domain.goal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketingagent.common.exception.BadRequestException;
import com.marketingagent.domain.ads.AdKeyword;
import com.marketingagent.domain.ads.AdKeywordRepository;
import com.marketingagent.domain.ads.EfficiencyMath;
import com.marketingagent.domain.ads.KeywordStatDailyRepository;
import com.marketingagent.domain.ads.account.AdAccountService;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.Settings;
import com.marketingagent.domain.ads.autopilot.AutopilotDtos.SettingsRequest;
import com.marketingagent.domain.ads.autopilot.AutopilotService;
import com.marketingagent.domain.ads.dto.KeywordAggregate;
import com.marketingagent.domain.ads.planner.PlanService;
import com.marketingagent.domain.ads.planner.PlanService.DraftGoal;
import com.marketingagent.domain.ads.planner.PlanService.DraftOutcome;
import com.marketingagent.domain.ads.planner.PlannerDtos.GoalType;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanKeyword;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanProposal;
import com.marketingagent.domain.ads.planner.PlannerDtos.PlanRequest;
import com.marketingagent.domain.ads.planner.PlannerDtos.Scenario;
import com.marketingagent.domain.goal.BusinessModel.Offering;
import com.marketingagent.domain.onboarding.BusinessProfile;
import com.marketingagent.domain.onboarding.BusinessProfileRepository;
import com.marketingagent.integration.naver.SearchAdClient;
import com.marketingagent.domain.goal.BusinessModel.Slot;
import com.marketingagent.domain.goal.BusinessModel.SlotType;
import com.marketingagent.domain.goal.GoalDtos.Analysis;
import com.marketingagent.domain.goal.GoalDtos.Checkin;
import com.marketingagent.domain.goal.GoalDtos.CheckinRequest;
import com.marketingagent.domain.goal.GoalDtos.CheckinResult;
import com.marketingagent.domain.goal.GoalDtos.ConfirmRequest;
import com.marketingagent.domain.goal.GoalDtos.GoalView;
import com.marketingagent.domain.goal.GoalDtos.Message;
import com.marketingagent.domain.goal.GoalDtos.Option;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목표 상담. 대화로 사업 구조를 파악하고, 그 구조에서 목표 CPA 와 필요한 문의 수를 계산한 뒤,
 * 네이버 키워드 수요와 예상 성과로 실현 가능한 운영안을 만들어 사용자가 고르게 한다.
 * 고른 안은 운영 계획·자동 운영·입찰 추천의 목표가 된다. 운영 중에는 실제 계약 결과를 받아 목표를 다시 맞춘다.
 *
 * <p>숫자는 모두 이 서비스와 {@link GoalEconomics}, {@link PlanService} 가 계산한다. AI 는 질문과 답 읽기만 한다.
 */
@Slf4j
@Service
public class GoalService {

    /** 실측 계약 비율이 적을 때 처음 값 쪽으로 당겨 잡는 강도(가상 문의 수). */
    static final double PRIOR_LEADS = 20;
    static final long MIN_TRIAL_BUDGET = 100_000;
    static final double TRIAL_SHARE = 0.5;
    /** 다시 계산한 목표 CPA 가 이만큼 넘게 달라지면 바꾸기를 권한다. */
    static final double RETARGET_THRESHOLD = 0.1;

    public static final String OPTION_GOAL = "GOAL";
    public static final String OPTION_LIMIT = "LIMIT";
    public static final String OPTION_TRIAL = "TRIAL";

    /** AI 상담에서 빠진 항목을 되묻는 최대 횟수. 넘으면 꼭 필요한 항목 말고는 가정으로 채우고 계산한다. */
    static final int MAX_FOLLOW_UPS = 2;
    static final double DEFAULT_CVR_PCT = 2.0;

    private final AdAccountService accountService;
    private final GoalSessionRepository sessionRepository;
    private final GoalMessageRepository messageRepository;
    private final GoalCheckinRepository checkinRepository;
    private final RuleBasedInterviewer rules;
    private final AiInterviewer ai;
    private final PlanService planService;
    private final AutopilotService autopilotService;
    private final KeywordStatDailyRepository statRepository;
    private final AdKeywordRepository keywordRepository;
    private final KeywordBidResearch keywordResearch;
    private final PerformanceDiagnosis performanceDiagnosis;
    private final ChannelScanner channelScanner;
    private final BusinessProfileRepository profileRepository;
    private final ObjectMapper objectMapper;
    private final boolean aiEnabled;

    public GoalService(AdAccountService accountService, GoalSessionRepository sessionRepository,
            GoalMessageRepository messageRepository, GoalCheckinRepository checkinRepository,
            RuleBasedInterviewer rules, AiInterviewer ai, PlanService planService, AutopilotService autopilotService,
            KeywordStatDailyRepository statRepository, AdKeywordRepository keywordRepository,
            KeywordBidResearch keywordResearch, PerformanceDiagnosis performanceDiagnosis, ChannelScanner channelScanner,
            BusinessProfileRepository profileRepository, ObjectMapper objectMapper,
            @Value("${ai.provider:stub}") String aiProvider) {
        this.performanceDiagnosis = performanceDiagnosis;
        this.channelScanner = channelScanner;
        this.profileRepository = profileRepository;
        this.accountService = accountService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.checkinRepository = checkinRepository;
        this.rules = rules;
        this.ai = ai;
        this.planService = planService;
        this.autopilotService = autopilotService;
        this.statRepository = statRepository;
        this.keywordRepository = keywordRepository;
        this.keywordResearch = keywordResearch;
        this.objectMapper = objectMapper;
        this.aiEnabled = !"stub".equalsIgnoreCase(aiProvider);
    }

    // --- 조회 ---

    /** 가장 최근 상담. 한 번도 상담하지 않았으면 null. */
    @Transactional(readOnly = true)
    public GoalView current(Long accountId) {
        Long account = accountService.require(accountId).getId();
        return sessionRepository.findTopByAdAccountIdOrderByIdDesc(account).map(this::view).orElse(null);
    }

    // --- 대화 ---

    static final String OPEN_QUESTION = """
            안녕하세요. 광고 목표와 전략을 함께 잡아 볼게요.

            사업을 편하게 소개해 주세요. 길어도 괜찮고, 아래 내용이 들어 있으면 더 정확해집니다. 모르는 건 빼셔도 됩니다.
            • 어떤 사업이고, 주 고객층은 누구인지 (고객층마다 사이트가 다르면 함께)
            • 상품·서비스별로 계약 1건에 받는 돈과 실제로 남는 돈, 대략의 판매 비중
            • 문의가 10건 오면 보통 몇 건이 계약되는지
            • 한 달에 광고로 얻고 싶은 것(매출·계약·문의)과 쓸 수 있는 광고비
            • 요즘 광고에서 고민인 점
            • 내 홈페이지 주소, 그리고 신경 쓰이는 경쟁 업체나 따라 하고 싶은 업계 선도 업체(이름·홈페이지)

            말씀을 정리해서 빠진 것만 한 번에 여쭤본 뒤, 목표 CPA 계산과 키워드별 입찰가 조사, 연동된 광고 실적 진단,
            경쟁 업체의 광고 채널 분석까지 해서 종합 제안을 드릴게요.""";

    static final String RETURN_QUESTION = """
            지난번에 알려 주신 사업 구조는 그대로 두었어요.
            이번 달 목표(매출·계약·문의 중 하나)와 쓸 수 있는 광고비, 그리고 그사이 바뀐 점이나 고민을 편하게 말씀해 주세요.""";

    /**
     * 새 상담을 연다. 전에 상담한 적이 있으면 사업 구조는 이어받고 목표만 다시 묻는다.
     */
    @Transactional
    public GoalView start(Long accountId) {
        Long account = accountService.require(accountId).getId();
        Optional<GoalSession> previous = sessionRepository.findTopByAdAccountIdOrderByIdDesc(account);

        BusinessModel model = previous.map(this::read).orElseGet(BusinessModel::new);
        model.setFollowUps(0);
        if (previous.isPresent()) {
            model.setGoalType(null);
            model.setGoalAmount(null);
            model.setBudgetLimit(null);
        }
        GoalSession session = sessionRepository.save(new GoalSession(account, json(model)));

        if (aiEnabled) {
            // AI 가 있으면 하나씩 캐묻지 않고, 넓게 한 번 묻고 그 답에서 한꺼번에 추린다.
            say(session, GoalMessage.ASSISTANT, previous.isEmpty() ? OPEN_QUESTION : RETURN_QUESTION);
            return view(session);
        }

        String greeting = previous.isEmpty()
                ? "안녕하세요. 광고 목표를 함께 정해 볼게요.\n사업 구조를 몇 가지 여쭤본 뒤, 문의 1건에 광고비를 얼마까지 써도 되는지(목표 CPA)와 "
                        + "한 달 예산을 계산해 드립니다. 모르는 건 '모름'이라고 하셔도 됩니다."
                : "지난번에 알려 주신 사업 구조는 그대로 두고, 이번 목표만 새로 정할게요.\n바뀐 내용이 있으면 오른쪽에서 고치거나 말씀해 주세요.";
        Slot slot = model.nextMissing();
        String question = slot == null ? "" : "\n\n" + rules.question(model, slot, suggestions(account));
        say(session, GoalMessage.ASSISTANT, greeting + question);
        return view(session);
    }

    /** 사용자의 말 한 마디를 받아 사업 구조를 채우고 다음 말을 돌려준다. 다 채워지면 바로 계산까지 한다. */
    @Transactional
    public GoalView reply(Long accountId, String text) {
        Long account = accountService.require(accountId).getId();
        GoalSession session = sessionRepository.findTopByAdAccountIdOrderByIdDesc(account).orElse(null);
        if (session == null) {
            start(account);
            session = sessionRepository.findTopByAdAccountIdOrderByIdDesc(account).orElseThrow();
        }
        List<GoalMessage> history = messageRepository.findBySessionIdOrderByIdAsc(session.getId());
        say(session, GoalMessage.USER, text);

        BusinessModel model = read(session);
        List<String> suggestions = suggestions(account);
        Slot slot = model.nextMissing();

        if (slot == null) {
            say(session, GoalMessage.ASSISTANT, afterComplete(account, session, model, history, text, suggestions));
            return view(session);
        }

        String before = json(model);
        List<String> notes = new ArrayList<>();
        String aiReply = null;
        boolean understood = false;

        if (aiEnabled) {
            Optional<AiInterviewer.Turn> turn = ai.next(model, history, text, slot, rules.question(model, slot, suggestions));
            if (turn.isPresent()) {
                BusinessModel updated = turn.get().model();
                applyUnknown(updated, turn.get().unknown(), suggestions, notes);
                updated.setFollowUps((updated.getFollowUps() == null ? 0 : updated.getFollowUps()) + 1);
                if (!json(updated).equals(before) || turn.get().reply() != null) {
                    model = updated;
                    aiReply = turn.get().reply();
                    understood = true;
                    // 꼭 필요한 것이 다 모였거나 되물을 만큼 되물었으면, 나머지는 묻지 않고 가정으로 채운다.
                    if (model.missingCritical().isEmpty() || model.getFollowUps() >= MAX_FOLLOW_UPS) {
                        fillAssumptions(model, suggestions, notes);
                    }
                }
            }
        }
        if (!understood) {
            RuleBasedInterviewer.Answer answer = rules.apply(model, slot, text, suggestions);
            understood = answer.understood();
            if (answer.note() != null) {
                notes.add(answer.note());
            }
        }

        session.updateModel(json(model));
        Slot next = model.nextMissing();
        String reply;
        if (!understood) {
            reply = join(notes) + "\n\n" + rules.question(model, slot, suggestions);
        } else if (next == null) {
            Analysis analysis = analyze(account, session, model);
            String lead = aiReply == null ? "" : aiReply + "\n\n";
            reply = lead + join(notes) + (notes.isEmpty() ? "" : "\n\n") + summary(model, analysis);
        } else {
            // 되물을 만큼 되물었는데도 꼭 필요한 항목이 비어 있으면, 그 항목만 정해진 질문으로 콕 집어 묻는다.
            boolean askedEnough = model.getFollowUps() != null && model.getFollowUps() >= MAX_FOLLOW_UPS;
            String ask = aiReply != null && !askedEnough ? aiReply : rules.question(model, next, suggestions);
            reply = join(notes) + (notes.isEmpty() ? "" : "\n\n") + ask;
        }
        say(session, GoalMessage.ASSISTANT, reply.strip());
        return view(session);
    }

    /** 오른쪽 패널에서 사업 구조를 직접 고쳤을 때. 다 채워져 있으면 다시 계산한다. */
    @Transactional
    public GoalView updateModel(Long accountId, BusinessModel edited) {
        Long account = accountService.require(accountId).getId();
        GoalSession session = sessionRepository.findTopByAdAccountIdOrderByIdDesc(account)
                .orElseThrow(() -> new BadRequestException("먼저 목표 상담을 시작하세요."));
        BusinessModel current = read(session);
        BusinessModel merged;
        try {
            merged = ai.merge(current, objectMapper.valueToTree(edited));
        } catch (Exception e) {
            throw new BadRequestException("고친 내용을 읽지 못했습니다: " + e.getMessage());
        }
        keepSources(current, merged);
        if (json(merged).equals(json(current))) {
            return view(session);
        }
        session.updateModel(json(merged));
        Slot next = merged.nextMissing();
        if (next == null) {
            Analysis analysis = analyze(account, session, merged);
            say(session, GoalMessage.ASSISTANT, "고친 내용으로 다시 계산했습니다.\n\n" + summary(merged, analysis));
        } else {
            say(session, GoalMessage.ASSISTANT, "고친 내용을 반영했습니다.\n\n" + rules.question(merged, next, suggestions(account)));
        }
        return view(session);
    }

    /** 같은 사업 구조로 다시 검토한다. 그사이 쌓인 실적과 네이버 예측이 새로 반영된다. */
    @Transactional
    public GoalView reanalyze(Long accountId) {
        Long account = accountService.require(accountId).getId();
        GoalSession session = sessionRepository.findTopByAdAccountIdOrderByIdDesc(account)
                .orElseThrow(() -> new BadRequestException("먼저 목표 상담을 시작하세요."));
        BusinessModel model = read(session);
        if (model.nextMissing() != null) {
            throw new BadRequestException("아직 채우지 않은 항목이 있습니다. 질문에 먼저 답해 주세요.");
        }
        Analysis analysis = analyze(account, session, model);
        say(session, GoalMessage.ASSISTANT, "최근 실적으로 다시 검토했습니다.\n\n" + summary(model, analysis));
        return view(session);
    }

    // --- 확정 ---

    /** 고른 안을 운영 목표로 확정한다. 운영 계획을 저장하고 자동 운영·입찰 추천의 목표 CPA 와 월 한도를 맞춘다. */
    @Transactional
    public GoalView confirm(Long accountId, ConfirmRequest request) {
        Long account = accountService.require(accountId).getId();
        GoalSession session = sessionRepository.findTopByAdAccountIdOrderByIdDesc(account)
                .orElseThrow(() -> new BadRequestException("먼저 목표 상담을 시작하세요."));
        Analysis analysis = readAnalysis(session)
                .orElseThrow(() -> new BadRequestException("아직 계산한 안이 없습니다. 질문에 끝까지 답하면 안을 만들어 드립니다."));
        Option option = analysis.options().stream()
                .filter(o -> o.key().equals(request.option()) && o.plan() != null)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("고를 수 없는 안입니다: " + request.option()));
        long targetCpa = analysis.economics().targetCpa();

        planService.save(account, option.plan(), Math.max(1, option.expectedLeads()));
        Settings current = autopilotService.settings(account);
        Settings applied = autopilotService.updateSettings(account, new SettingsRequest(
                request.enableAutopilot() || current.enabled(), targetCpa, 0, option.monthlyBudget(),
                current.manageBids(), current.manageBudget(), current.pauseWasteful()));
        accountService.changeTargetCpa(account, targetCpa);
        session.confirm(option.key());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("「%s」으로 확정했습니다.\n", option.label()));
        sb.append(String.format("• 목표 CPA %s: 입찰가 추천과 자동 운영이 이 값을 기준으로 삼습니다.\n", won(targetCpa)));
        sb.append(String.format("• 한 달 광고비 상한 %s, 하루 %s 안팎\n", won(option.monthlyBudget()), won(option.dailyBudget())));
        if (applied.enabled()) {
            sb.append("• 자동 운영이 켜져 있습니다. 매일 아침 한도 안에서 입찰가·일예산을 조정합니다.");
            if (applied.dryRun()) {
                sb.append(" 지금 서버는 시험 모드(dry-run)라 네이버에 반영하지 않고 기록만 남깁니다.");
            }
            sb.append('\n');
        } else {
            sb.append("• 자동 운영은 꺼져 있습니다. '입찰가 조정' 화면에서 추천을 보고 직접 승인하시면 됩니다.\n");
        }
        sb.append("\n일주일쯤 지나면 그동안 실제로 계약된 건수를 알려 주세요. 문의→계약 비율을 실제 값으로 바꿔 목표 CPA를 다시 맞춥니다.");
        say(session, GoalMessage.ASSISTANT, sb.toString());
        log.info("목표 확정: 계정 {} 안 {} 목표 CPA {}원 월 {}원", account, option.key(), targetCpa, option.monthlyBudget());
        return view(session);
    }

    // --- 실제 결과 반영 ---

    /**
     * 실제 계약 결과를 받아 문의→계약 비율을 다시 잡는다. 결과가 적을 때는 처음 값 쪽으로 당겨서 한두 주의 우연에 휘둘리지 않게 한다.
     * 목표 CPA 는 바로 바꾸지 않고 새 값을 제안한다. 바꾸려면 {@link #applyTarget} 을 부른다.
     */
    @Transactional
    public CheckinResult checkin(Long accountId, CheckinRequest request) {
        Long account = accountService.require(accountId).getId();
        if (request.until().isBefore(request.since())) {
            throw new BadRequestException("기간의 끝이 시작보다 앞설 수 없습니다.");
        }
        GoalSession session = sessionRepository.findTopByAdAccountIdAndStatusOrderByIdDesc(account, GoalSession.CONFIRMED)
                .or(() -> sessionRepository.findTopByAdAccountIdOrderByIdDesc(account))
                .orElseThrow(() -> new BadRequestException("먼저 목표 상담을 마쳐 주세요."));
        BusinessModel model = read(session);
        if (model.nextMissing() != null) {
            throw new BadRequestException("목표 상담을 끝까지 마친 뒤에 결과를 알려 주세요.");
        }

        long leads;
        String leadsSource;
        if (request.leads() != null) {
            leads = request.leads();
            leadsSource = GoalCheckin.LEADS_USER;
        } else {
            leads = statRepository.aggregateByKeyword(account, request.since(), request.until()).stream()
                    .mapToLong(r -> r.ccnt() == null ? 0 : r.ccnt()).sum();
            leadsSource = GoalCheckin.LEADS_NAVER;
            if (leads == 0) {
                throw new BadRequestException("이 기간 네이버에 잡힌 문의(전환)가 없습니다. 전환 추적을 안 쓰고 계시면 문의 수를 직접 넣어 주세요.");
            }
        }
        if (request.deals() > leads) {
            throw new BadRequestException(String.format("계약 %,d건이 문의 %,d건보다 많을 수 없습니다. 문의 수를 직접 넣어 주세요.",
                    request.deals(), leads));
        }
        GoalCheckin saved = checkinRepository.save(new GoalCheckin(account, request.since(), request.until(), leads,
                leadsSource, request.deals(), request.revenue()));

        List<GoalCheckin> all = checkinRepository.findByAdAccountIdOrderBySinceDesc(account);
        long totalLeads = all.stream().mapToLong(GoalCheckin::getLeads).sum();
        long totalDeals = all.stream().mapToLong(GoalCheckin::getDeals).sum();
        double measured = totalLeads == 0 ? 0 : totalDeals * 100.0 / totalLeads;

        double previous = model.getLeadToDealPct();
        double prior = model.getLeadToDealPriorPct() != null ? model.getLeadToDealPriorPct() : previous;
        double blended = round1(EfficiencyMath.shrunkRate(totalDeals, totalLeads, prior / 100.0, PRIOR_LEADS) * 100.0);
        model.setLeadToDealPriorPct(prior);
        model.setLeadToDealPct(Math.max(0.1, blended));
        model.setLeadToDealSource(BusinessModel.SOURCE_MEASURED);
        session.updateModel(json(model));

        long suggested = GoalEconomics.compute(model).targetCpa();
        long applied = autopilotService.settings(account).effectiveTargetCpa();
        boolean bigChange = applied > 0 && Math.abs(suggested - applied) > applied * RETARGET_THRESHOLD;

        String message = String.format(
                "알려 주신 결과까지 합치면 문의 %,d건 중 계약 %,d건(%s)입니다. 처음 값 %s와 섞어 문의→계약 비율을 %s로 고쳤습니다.\n"
                        + "이 비율이면 목표 CPA는 %s입니다. (지금 적용 중: %s)\n%s",
                totalLeads, totalDeals, GoalEconomics.pct(round1(measured)), GoalEconomics.pct(prior),
                GoalEconomics.pct(model.getLeadToDealPct()), won(suggested), applied > 0 ? won(applied) : "없음",
                applied == 0 ? "목표를 확정하면 이 값이 적용됩니다."
                        : bigChange ? "차이가 커서 새 목표 CPA로 바꾸시길 권합니다. '새 목표 CPA 적용'을 누르면 바뀝니다."
                                : "지금 값과 크게 다르지 않아 그대로 두셔도 됩니다.");
        if (totalLeads < PRIOR_LEADS) {
            message += String.format("\n아직 문의가 %,d건이라 처음 값의 영향이 큽니다. 결과가 쌓일수록 실제 값에 가까워집니다.", totalLeads);
        }
        say(session, GoalMessage.ASSISTANT, message);

        return new CheckinResult(toDto(saved), totalLeads, totalDeals, round1(measured), previous,
                model.getLeadToDealPct(), applied, suggested, message, view(session));
    }

    /** 실제 결과로 다시 계산한 목표 CPA 를 자동 운영과 입찰 추천에 적용한다. */
    @Transactional
    public GoalView applyTarget(Long accountId) {
        Long account = accountService.require(accountId).getId();
        GoalSession session = sessionRepository.findTopByAdAccountIdAndStatusOrderByIdDesc(account, GoalSession.CONFIRMED)
                .orElseThrow(() -> new BadRequestException("확정한 목표가 없습니다. 먼저 안을 골라 확정하세요."));
        BusinessModel model = read(session);
        long targetCpa = GoalEconomics.compute(model).targetCpa();
        Settings current = autopilotService.settings(account);
        autopilotService.updateSettings(account, new SettingsRequest(current.enabled(), targetCpa, current.targetRoasPct(),
                current.monthlyBudgetCap(), current.manageBids(), current.manageBudget(), current.pauseWasteful()));
        accountService.changeTargetCpa(account, targetCpa);
        say(session, GoalMessage.ASSISTANT, String.format("목표 CPA를 %s로 바꿨습니다. 다음 입찰가 추천과 자동 운영부터 이 값을 씁니다.",
                won(targetCpa)));
        return view(session);
    }

    // --- 계산과 검토 ---

    private Analysis analyze(Long account, GoalSession session, BusinessModel model) {
        GoalEconomics.Result econ = GoalEconomics.compute(model);
        if (econ.valuePerLead() <= 0 || econ.targetCpa() <= 0) {
            throw new BadRequestException("계약 1건으로 남는 돈이 0원이라 광고비를 쓸 여지가 없습니다. 계약 1건 이익을 다시 확인해 주세요.");
        }
        long limit = model.getBudgetLimit() == null ? 0 : model.getBudgetLimit();
        long trialBase = limit > 0 ? Math.min(limit, econ.budgetAtTargetCpa()) : econ.budgetAtTargetCpa();
        long trialBudget = roundUp(Math.max(MIN_TRIAL_BUDGET, Math.round(trialBase * TRIAL_SHARE)), 10_000);

        // 전환 1건에 쓸 수 있는 돈 = 문의 1건의 가치 × 광고비 비율. 계획 엔진은 이를 "매출 ÷ 목표 수익률"로 받는다.
        double roasPct = 100.0 * 100.0 / model.getAdSharePct();
        PlanRequest request = new PlanRequest(model.getSeedKeywords(), GoalType.CONVERSIONS, econ.leadsNeeded(), null,
                econ.valuePerLead(), null, null, null, roasPct);

        List<DraftGoal> goals = new ArrayList<>();
        goals.add(new DraftGoal(OPTION_GOAL, GoalType.CONVERSIONS, Math.max(1, econ.leadsNeeded()), null));
        if (limit > 0) {
            goals.add(new DraftGoal(OPTION_LIMIT, GoalType.BUDGET, null, limit));
        }
        goals.add(new DraftGoal(OPTION_TRIAL, GoalType.BUDGET, null, trialBudget));

        List<Option> options = new ArrayList<>();
        for (DraftOutcome outcome : planService.drafts(account, request, goals)) {
            options.add(option(outcome, econ, limit));
        }
        Option goal = find(options, OPTION_GOAL);
        // 목표 달성에 드는 돈이 시험 운영 예산이나 상한보다 작으면 그 안들은 의미가 없다.
        if (goal != null && goal.plan() != null) {
            options.removeIf(o -> o.key().equals(OPTION_TRIAL) && o.monthlyBudget() >= goal.monthlyBudget());
            options.removeIf(o -> o.key().equals(OPTION_LIMIT) && o.monthlyBudget() >= goal.monthlyBudget());
        }

        List<String> notes = new ArrayList<>();
        boolean measuredCvr = options.stream().map(Option::plan).filter(Objects::nonNull).findFirst()
                .map(p -> p.basis().stream().anyMatch(b -> "전환율".equals(b.item()) && "실측".equals(b.grade())))
                .orElse(false);
        if (!measuredCvr) {
            notes.add("클릭이 문의로 이어지는 비율(전환율)은 아직 이 계정의 실적이 충분하지 않아 가정한 값입니다. 운영 2~3주면 실측으로 바뀝니다.");
        }
        if (BusinessModel.SOURCE_ASSUMED.equals(model.getLeadToDealSource())) {
            notes.add(String.format("문의→계약 비율 %s는 가정입니다. 실제 계약 결과를 알려 주시면 그 값으로 다시 맞춥니다.",
                    GoalEconomics.pct(model.getLeadToDealPct())));
        }
        if (model.getOfferings().stream().anyMatch(o -> BusinessModel.SOURCE_ASSUMED.equals(o.getProfitSource()))) {
            notes.add("계약 1건 이익 중 가정한 값이 있습니다. 실제와 다르면 오른쪽에서 고쳐 주세요.");
        }
        if (goal != null && goal.plan() != null && !goal.meetsGoal()) {
            notes.add(String.format("지금 키워드와 목표 CPA로는 문의가 한 달 %,d건 안팎까지만 예상됩니다(목표 %,d건). "
                            + "키워드를 넓히거나, 광고비 비율을 높이거나, 목표를 낮추는 방법이 있습니다.",
                    goal.expectedLeads(), econ.leadsNeeded()));
        }
        if (model.getRevenueModel() == BusinessModel.RevenueModel.SUBSCRIPTION) {
            notes.add("정기 결제는 첫 달 결제액만 그 달 매출로 셉니다. 이후 달의 결제는 문의 1건의 가치(목표 CPA)에 반영돼 있습니다.");
        }

        String[] recommendation = recommend(model, options, measuredCvr, limit);

        // 이미 광고를 하던 계정이면 최근 실적부터 진단한다. 유입 검색어에서 찾은 새 키워드는 입찰가 조사에 맨 앞으로 넣는다.
        PerformanceDiagnosis.Result diagnosis = null;
        try {
            diagnosis = performanceDiagnosis.diagnose(account, econ.targetCpa());
        } catch (RuntimeException e) {
            log.warn("실적 진단 실패: {}", e.getMessage());
            notes.add("최근 광고 실적을 진단하지 못했습니다. 잠시 뒤 '다시 검토'를 눌러 주세요.");
        }
        List<BusinessModel.KeywordGroup> groups = new ArrayList<>();
        if (diagnosis != null && !diagnosis.newKeywords().isEmpty()) {
            groups.add(new BusinessModel.KeywordGroup("유입 검색어에서 발견",
                    diagnosis.newKeywords().stream().map(PerformanceDiagnosis.KeywordIdea::keyword).limit(5).toList()));
        }
        groups.addAll(keywordGroups(model, suggestions(account)));

        SearchAdClient client = null;
        KeywordBidResearch.MarketCheck market = null;
        PlanProposal basePlan = options.stream().map(Option::plan).filter(Objects::nonNull).findFirst().orElse(null);
        try {
            client = accountService.scope(account).client();
            market = keywordResearch.research(client, groups,
                    basePlan == null ? DEFAULT_CVR_PCT : basePlan.cvrPct(),
                    basePlan == null ? "기본 가정값" : basePlan.cvrSource(), econ);
        } catch (RuntimeException e) {
            log.warn("키워드 입찰가 조사 실패: {}", e.getMessage());
            notes.add("키워드별 입찰가를 조사하지 못했습니다. 네이버 연결을 확인한 뒤 '다시 검토'를 눌러 주세요.");
        }

        ChannelScanner.Result competition = competition(account, session, model, client);
        String solution = solution(model, econ, market, diagnosis, competition, options, recommendation);
        Analysis analysis = new Analysis(econ, options, recommendation[0], recommendation[1], notes, LocalDateTime.now(),
                market, solution, diagnosis, competition);
        session.analyzed(json(analysis));
        return analysis;
    }

    private Option option(DraftOutcome outcome, GoalEconomics.Result econ, long limit) {
        String key = outcome.key();
        String label = switch (key) {
            case OPTION_GOAL -> "목표 달성안";
            case OPTION_LIMIT -> "예산 상한안";
            default -> "시험 운영안";
        };
        String description = switch (key) {
            case OPTION_GOAL -> String.format("목표 문의 %,d건을 채우는 데 필요한 만큼 씁니다. 문의 1건이 목표 CPA %s을 넘게 드는 클릭은 사지 않습니다.",
                    econ.leadsNeeded(), won(econ.targetCpa()));
            case OPTION_LIMIT -> String.format("정해 두신 한 달 상한 %s 안에서 효율이 좋은 키워드부터 씁니다.", won(limit));
            default -> "2~3주 동안 작게 돌려 실제 전환율과 계약 비율을 잰 뒤, 그 값으로 예산을 다시 잡습니다.";
        };
        PlanProposal plan = outcome.plan();
        if (plan == null) {
            return new Option(key, label, description, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, List.of(), null,
                    outcome.error());
        }
        long cost = plan.keywords().stream().filter(PlanKeyword::selected).mapToLong(PlanKeyword::expectedCost).sum();
        long leads = plan.expectedConversions();
        long low = plan.scenarios().stream().mapToLong(Scenario::conversions).min().orElse(leads);
        long high = plan.scenarios().stream().mapToLong(Scenario::conversions).max().orElse(leads);
        double rate = econ.leadToDealPct() / 100.0;
        long deals = Math.round(leads * rate);
        long revenue = deals * econ.revenuePerDeal();
        long profit = deals * econ.profitPerDeal() - cost;
        long cpa = leads == 0 ? 0 : cost / leads;
        boolean meets = leads >= econ.leadsNeeded();
        double goalRate = econ.leadsNeeded() == 0 ? 0 : round1(leads * 100.0 / econ.leadsNeeded());
        return new Option(key, label, description, plan.monthlyBudget(), plan.dailyBudget(), cost, leads, low, high,
                deals, revenue, profit, cpa, meets, goalRate, plan.warnings(), plan, null);
    }

    /** @return [추천 안 key, 이유] */
    private String[] recommend(BusinessModel model, List<Option> options, boolean measuredCvr, long limit) {
        Option goal = find(options, OPTION_GOAL);
        Option capped = find(options, OPTION_LIMIT);
        Option trial = find(options, OPTION_TRIAL);
        boolean goalOk = goal != null && goal.plan() != null;
        if ((model.hasAssumptions() || !measuredCvr) && trial != null && trial.plan() != null) {
            return new String[] {OPTION_TRIAL,
                    "계산에 아직 가정한 숫자가 들어 있습니다. 큰돈을 쓰기 전에 작게 돌려 실제 값을 먼저 재는 편이 안전합니다."};
        }
        if (goalOk && goal.meetsGoal() && (limit == 0 || goal.monthlyBudget() <= limit)) {
            return new String[] {OPTION_GOAL, "목표를 채울 수 있고, 문의 1건 비용도 목표 CPA 안에 들어옵니다."};
        }
        if (capped != null && capped.plan() != null) {
            return new String[] {OPTION_LIMIT, "목표를 다 채우려면 상한보다 많이 들어서, 상한 안에서 효율이 좋은 키워드부터 씁니다."};
        }
        if (goalOk) {
            return new String[] {OPTION_GOAL, "목표를 다 채우지는 못하지만, 목표 CPA 안에서 살 수 있는 문의를 최대한 삽니다."};
        }
        return options.stream().filter(o -> o.plan() != null).findFirst()
                .map(o -> new String[] {o.key(), "만들 수 있는 안이 이것뿐입니다."})
                .orElse(new String[] {null, "지금 조건으로는 살 수 있는 클릭이 없습니다. 아래 안내를 확인해 주세요."});
    }

    /** 상담 대화창에 보여 줄 계산 요약. 숫자는 모두 계산 결과를 그대로 쓴다. */
    private String summary(BusinessModel model, Analysis a) {
        GoalEconomics.Result e = a.economics();
        StringBuilder sb = new StringBuilder("계산을 마쳤어요.\n");
        boolean subscription = model.getRevenueModel() == BusinessModel.RevenueModel.SUBSCRIPTION;
        sb.append(String.format("• 문의 1건의 가치(첫 계약 기준): %s 이익 %s × 계약 비율 %s%s = %s\n",
                subscription ? "월" : "계약 1건", won(e.profitPerDeal()), GoalEconomics.pct(e.leadToDealPct()),
                subscription ? " × 유지 개월(최대 12)" : "", won(e.valuePerLead())));
        sb.append(String.format("• 그중 %s를 광고비로 쓰면 목표 CPA %s · 손익분기 %s", GoalEconomics.pct(model.getAdSharePct()),
                won(e.targetCpa()), won(e.breakEvenCpa())));
        if (e.ltvCpa() > e.targetCpa()) {
            sb.append(String.format(" · 재계약까지 친 상한 %s(참고용)", won(e.ltvCpa())));
        }
        sb.append('\n');
        if (a.market() != null && a.market().marketCpa() > 0) {
            sb.append(String.format("• 네이버 시세로 본 문의 1건 비용: 약 %s (키워드 %d개를 평균 3위로 걸 때, %s)\n",
                    won(a.market().marketCpa()), a.market().keywords().size(), verdictLabel(a.market().verdict())));
        }
        sb.append(String.format("• 목표를 채우려면 한 달에 계약 %,d건, 문의 %,d건이 필요합니다.\n", e.dealsNeeded(), e.leadsNeeded()));
        if (a.diagnosis() != null && a.diagnosis().hasData()) {
            PerformanceDiagnosis.Result d = a.diagnosis();
            sb.append(String.format("• 최근 30일 실적 진단: 광고비 %s · 전환 %,d건%s → 문제점 %d건, 개선점 %d건\n", won(d.cost()),
                    d.conversions(), d.accountCpa() > 0 ? " · 문의 1건 " + won(d.accountCpa()) : "",
                    d.problems().size(), d.improvements().size()));
        }
        if (a.competition() != null) {
            sb.append(String.format("• 경쟁사·선도 업체 %d곳의 광고 채널을 살펴봤습니다(권유 %d건).\n",
                    a.competition().sites().stream().filter(s -> !"MINE".equals(s.source())).count(),
                    a.competition().suggestions().size()));
        }
        sb.append('\n');
        sb.append("네이버 검색량과 예상 클릭으로 따져 본 운영안:\n");
        for (Option o : a.options()) {
            if (o.plan() == null) {
                sb.append(String.format("• %s: 만들 수 없음 (%s)\n", o.label(), o.error()));
            } else {
                sb.append(String.format("• %s: 한 달 %s → 문의 약 %,d건, 계약 약 %,d건 (목표의 %s)\n", o.label(),
                        won(o.monthlyBudget()), o.expectedLeads(), o.expectedDeals(), GoalEconomics.pct(o.goalRatePct())));
            }
        }
        if (a.recommended() != null) {
            sb.append(String.format("\n추천: %s. %s\n", find(a.options(), a.recommended()).label(), a.recommendReason()));
            sb.append("오른쪽에서 안을 골라 확정하면, 이 목표 CPA와 예산으로 입찰가 추천·자동 운영이 움직입니다.");
        } else {
            sb.append('\n').append(a.recommendReason());
        }
        if (a.solution() != null && !a.solution().isBlank()) {
            sb.append("\n\n").append(a.solution());
        }
        return sb.toString();
    }

    /** 종합 제안. AI 가 있으면 서버 숫자만 넘겨 쓰게 하고, 없거나 실패하면 정해진 틀로 만든다. */
    private String solution(BusinessModel model, GoalEconomics.Result econ, KeywordBidResearch.MarketCheck market,
            PerformanceDiagnosis.Result diagnosis, ChannelScanner.Result competition, List<Option> options,
            String[] recommendation) {
        if (aiEnabled) {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("업종", model.getIndustry());
            facts.put("고객층", model.getSegments());
            facts.put("고민", model.getConcerns());
            facts.put("상품", model.getOfferings());
            facts.put("가정여부", model.hasAssumptions() ? "계산에 가정한 숫자가 들어 있음" : "모두 사장님이 알려 준 값");
            Map<String, Object> economics = new LinkedHashMap<>();
            economics.put("목표CPA_첫계약기준", econ.targetCpa());
            economics.put("손익분기CPA", econ.breakEvenCpa());
            economics.put("재계약포함_상한CPA", econ.ltvCpa());
            economics.put("필요한_월계약", econ.dealsNeeded());
            economics.put("필요한_월문의", econ.leadsNeeded());
            facts.put("계산", economics);
            if (market != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("시장예상CPA", market.marketCpa());
                m.put("판정", verdictLabel(market.verdict()));
                m.put("설명", market.explanation());
                m.put("전환율_퍼센트", market.cvrPct());
                m.put("키워드", market.keywords().stream().map(k -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("키워드", k.keyword());
                    row.put("묶음", k.group());
                    row.put("월검색량", k.monthlySearches());
                    row.put("입찰가_1위", k.bid1());
                    row.put("입찰가_3위", k.bid3());
                    row.put("입찰가_5위", k.bid5());
                    row.put("예상월클릭_3위", k.expectedClicks());
                    row.put("예상월비용_3위", k.expectedCost());
                    row.put("예상CPA", k.expectedCpa());
                    row.put("판정", verdictLabel(k.verdict()));
                    return row;
                }).toList());
                facts.put("네이버조사", m);
            }
            facts.put("운영안", options.stream().filter(o -> o.plan() != null).map(o -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("안", o.label());
                row.put("월예산", o.monthlyBudget());
                row.put("예상월문의", o.expectedLeads());
                row.put("예상CPA", o.expectedCpa());
                return row;
            }).toList());
            Option recommended = recommendation[0] == null ? null : find(options, recommendation[0]);
            facts.put("추천안", recommended == null ? "없음" : recommended.label());
            if (diagnosis != null && diagnosis.hasData()) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("기간", diagnosis.since() + " ~ " + diagnosis.until());
                d.put("광고비", diagnosis.cost());
                d.put("클릭", diagnosis.clicks());
                d.put("전환", diagnosis.conversions());
                d.put("실제CPA", diagnosis.accountCpa());
                d.put("문제점", diagnosis.problems());
                d.put("개선점", diagnosis.improvements());
                d.put("새키워드후보", diagnosis.newKeywords());
                d.put("제외키워드후보", diagnosis.negativeCandidates());
                facts.put("실적진단", d);
            }
            if (competition != null && !competition.sites().isEmpty()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("살펴본업체", competition.sites());
                c.put("권유", competition.suggestions());
                c.put("한계", competition.limits());
                facts.put("경쟁사분석", c);
            }
            Optional<String> text = ai.solution(json(facts));
            if (text.isPresent()) {
                return text.get();
            }
        }
        return ruleSolution(market, diagnosis, competition);
    }

    /** AI 없이 만드는 종합 제안. 계산·조사·진단 결과를 정해진 틀에 담는다. */
    static String ruleSolution(KeywordBidResearch.MarketCheck market, PerformanceDiagnosis.Result diagnosis,
            ChannelScanner.Result competition) {
        StringBuilder sb = new StringBuilder();
        if (market != null && !market.keywords().isEmpty()) {
            sb.append("■ 한 줄 진단\n• ").append(market.explanation()).append('\n');
            sb.append("■ 먼저 쓸 키워드\n");
            market.keywords().stream().filter(k -> k.bid3() > 0).limit(5).forEach(k -> sb.append(String.format(
                    "• %s: 3위 입찰가 %s · 예상 문의 1건 %s (%s)\n", k.keyword(), won(k.bid3()),
                    k.expectedCpa() > 0 ? won(k.expectedCpa()) : "예측 없음", verdictLabel(k.verdict()))));
            Map<String, List<String>> byGroup = new LinkedHashMap<>();
            market.keywords().forEach(k -> byGroup.computeIfAbsent(k.group(), g -> new ArrayList<>()).add(k.keyword()));
            sb.append("■ 고객층·의도별 키워드 묶음\n");
            byGroup.forEach((g, ks) -> sb.append("• ").append(g).append(": ").append(String.join(", ", ks)).append('\n'));
        }
        if (diagnosis != null && diagnosis.hasData()
                && (!diagnosis.problems().isEmpty() || !diagnosis.improvements().isEmpty())) {
            sb.append("■ 지금 광고의 문제점과 개선\n");
            diagnosis.problems().stream().limit(4).forEach(f -> sb.append("• [문제] ").append(f.title()).append(": ")
                    .append(f.evidence()).append(' ').append(f.action()).append('\n'));
            diagnosis.improvements().stream().limit(3).forEach(f -> sb.append("• [개선] ").append(f.title()).append(": ")
                    .append(f.evidence()).append(' ').append(f.action()).append('\n'));
        }
        if (competition != null && !competition.suggestions().isEmpty()) {
            sb.append("■ 경쟁사·선도 업체에서 배울 광고 방법 (추정)\n");
            competition.suggestions().stream().limit(4).forEach(s -> {
                sb.append("• ").append(s.suggestion()).append('\n');
                s.evidence().stream().limit(2).forEach(line -> sb.append("  - 근거: ").append(line).append('\n'));
            });
        }
        String text = sb.toString().strip();
        return text.isEmpty() ? null : text;
    }

    /**
     * 경쟁사·선도 업체의 광고 채널을 살펴본다. 사장님이 경쟁사를 말하지 않았으면 AI 가 업계 선도 업체를 추정하고,
     * 그 목록은 사업 구조에 저장해 사장님이 오른쪽 패널에서 고칠 수 있게 한다.
     */
    private ChannelScanner.Result competition(Long account, GoalSession session, BusinessModel model, SearchAdClient client) {
        List<BusinessModel.Competitor> rivals = model.getCompetitors() == null ? List.of() : model.getCompetitors();
        if (rivals.isEmpty() && aiEnabled && model.getIndustry() != null) {
            rivals = ai.suggestCompetitors(model);
            if (!rivals.isEmpty()) {
                model.setCompetitors(new ArrayList<>(rivals));
                session.updateModel(json(model));
            }
        }
        if (rivals.stream().noneMatch(c -> c.getUrl() != null && !c.getUrl().isBlank())) {
            return null;
        }
        List<String> mine = new ArrayList<>(model.getWebsites() == null ? List.of() : model.getWebsites());
        profileRepository.findByAdAccountId(account).map(BusinessProfile::getHomepageUrl)
                .filter(u -> u != null && !u.isBlank()).ifPresent(u -> {
                    if (!mine.contains(u)) {
                        mine.add(u);
                    }
                });
        try {
            return channelScanner.scan(mine, rivals, client);
        } catch (RuntimeException e) {
            log.warn("경쟁사 채널 분석 실패: {}", e.getMessage());
            return null;
        }
    }

    static String verdictLabel(String verdict) {
        return switch (verdict == null ? "" : verdict) {
            case KeywordBidResearch.WITHIN -> "목표 이내";
            case KeywordBidResearch.OVER_TARGET -> "첫 계약으론 초과";
            case KeywordBidResearch.OVER_LTV -> "상한도 초과";
            default -> "예측 없음";
        };
    }

    /** 다 채운 뒤에 온 말. AI 가 있으면 고칠 내용을 읽어 다시 계산하고, 없으면 패널에서 고치도록 안내한다. */
    private String afterComplete(Long account, GoalSession session, BusinessModel model, List<GoalMessage> history,
            String text, List<String> suggestions) {
        if (aiEnabled) {
            Optional<AiInterviewer.Turn> turn = ai.next(model, history, text, null, null);
            if (turn.isPresent() && !json(turn.get().model()).equals(json(model))) {
                BusinessModel updated = turn.get().model();
                List<String> notes = new ArrayList<>();
                applyUnknown(updated, turn.get().unknown(), suggestions, notes);
                fillAssumptions(updated, suggestions, notes);
                session.updateModel(json(updated));
                if (updated.nextMissing() == null) {
                    return "말씀하신 내용으로 다시 계산했습니다.\n\n" + (notes.isEmpty() ? "" : join(notes) + "\n\n")
                            + summary(updated, analyze(account, session, updated));
                }
                return rules.question(updated, updated.nextMissing(), suggestions);
            }
            if (turn.isPresent() && turn.get().reply() != null) {
                return turn.get().reply();
            }
        }
        return "계산 결과는 오른쪽에 정리돼 있어요. 고칠 숫자가 있으면 오른쪽 '사업 구조'에서 바로 고치실 수 있고, "
                + "마음에 드는 안을 골라 '이 안으로 확정'을 누르시면 됩니다.";
    }

    /**
     * 꼭 필요하지 않은 빈 항목을 묻지 않고 채운다. 숫자는 정해 둔 보수적인 가정으로, 키워드는 상담에서 나온 묶음 →
     * 이 계정의 주력 키워드 → 상품 이름 순으로. 꼭 필요한 항목(업종·상품·매출·목표)이 비어 있으면 거기서 멈춘다.
     */
    private void fillAssumptions(BusinessModel model, List<String> suggestions, List<String> notes) {
        Set<SlotType> fillable = Set.of(SlotType.OFFERING_PROFIT, SlotType.LEAD_TO_DEAL, SlotType.DEALS_PER_CUSTOMER,
                SlotType.AD_SHARE, SlotType.BUDGET_LIMIT, SlotType.KEYWORDS);
        for (int guard = 0; guard < 20; guard++) {
            Slot next = model.nextMissing();
            if (next == null || !fillable.contains(next.type())) {
                return;
            }
            if (next.type() == SlotType.KEYWORDS) {
                model.setSeedKeywords(defaultSeeds(model, suggestions));
                if (model.getSeedKeywords().isEmpty()) {
                    return;
                }
                continue;
            }
            RuleBasedInterviewer.Answer answer = rules.apply(model, next, "모름", suggestions);
            if (!answer.understood()) {
                return;
            }
            if (next.type() == SlotType.BUDGET_LIMIT) {
                notes.add("한 달 광고비 상한은 말씀이 없어서 정해 두지 않고 계산했습니다.");
            } else if (answer.note() != null) {
                notes.add(answer.note());
            }
        }
    }

    private static List<String> defaultSeeds(BusinessModel model, List<String> suggestions) {
        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        if (model.getKeywordGroups() != null) {
            model.getKeywordGroups().forEach(g -> seeds.addAll(g.getKeywords()));
        }
        if (seeds.isEmpty()) {
            seeds.addAll(suggestions);
        }
        if (seeds.isEmpty() && model.getOfferings() != null) {
            model.getOfferings().forEach(o -> seeds.add(o.getName()));
        }
        return new ArrayList<>(seeds.stream().limit(5).toList());
    }

    /** 키워드 조사에 쓸 묶음. AI 가 묶음을 만들지 않았으면 핵심 키워드와 이 계정의 주력 키워드로 대신한다. */
    private static List<BusinessModel.KeywordGroup> keywordGroups(BusinessModel model, List<String> suggestions) {
        if (model.getKeywordGroups() != null && !model.getKeywordGroups().isEmpty()) {
            return model.getKeywordGroups();
        }
        List<BusinessModel.KeywordGroup> groups = new ArrayList<>();
        groups.add(new BusinessModel.KeywordGroup("핵심 키워드", model.getSeedKeywords()));
        List<String> extra = suggestions.stream().filter(k -> !model.getSeedKeywords().contains(k)).toList();
        if (!extra.isEmpty()) {
            groups.add(new BusinessModel.KeywordGroup("지금 광고 중인 키워드", extra));
        }
        return groups;
    }

    /** AI 가 "모른다"고 표시한 항목을 정해진 가정으로 채운다. */
    private void applyUnknown(BusinessModel model, List<String> unknown, List<String> suggestions, List<String> notes) {
        if (unknown == null || unknown.isEmpty()) {
            return;
        }
        Set<SlotType> types = new LinkedHashSet<>();
        for (String field : unknown) {
            String f = field == null ? "" : field.toLowerCase();
            if (f.contains("leadtodeal")) {
                types.add(SlotType.LEAD_TO_DEAL);
            } else if (f.contains("dealspercustomer")) {
                types.add(SlotType.DEALS_PER_CUSTOMER);
            } else if (f.contains("profit")) {
                types.add(SlotType.OFFERING_PROFIT);
            } else if (f.contains("adshare")) {
                types.add(SlotType.AD_SHARE);
            } else if (f.contains("budget")) {
                types.add(SlotType.BUDGET_LIMIT);
            }
        }
        for (int guard = 0; guard < 10; guard++) {
            Slot next = model.nextMissing();
            if (next == null || !types.contains(next.type())) {
                return;
            }
            RuleBasedInterviewer.Answer answer = rules.apply(model, next, "모름", suggestions);
            if (!answer.understood()) {
                return;
            }
            if (answer.note() != null) {
                notes.add(answer.note());
            }
        }
    }

    /** 패널에서 값을 그대로 둔 항목은 원래 출처(가정·실측)를 유지한다. */
    private static void keepSources(BusinessModel before, BusinessModel after) {
        if (Objects.equals(before.getLeadToDealPct(), after.getLeadToDealPct())) {
            after.setLeadToDealSource(before.getLeadToDealSource());
        }
        if (Objects.equals(before.getDealsPerCustomer(), after.getDealsPerCustomer())) {
            after.setDealsPerCustomerSource(before.getDealsPerCustomerSource());
        }
        if (Objects.equals(before.getAdSharePct(), after.getAdSharePct())) {
            after.setAdShareSource(before.getAdShareSource());
        }
        Map<String, Offering> old = before.getOfferings().stream()
                .collect(Collectors.toMap(Offering::getName, Function.identity(), (a, b) -> a));
        for (Offering o : after.getOfferings()) {
            Offering prev = old.get(o.getName());
            if (prev != null && Objects.equals(prev.getProfitPerDeal(), o.getProfitPerDeal())) {
                o.setProfitSource(prev.getProfitSource());
            }
        }
    }

    /** 이 계정에서 광고비를 많이 쓴 키워드. 실적이 없으면 등록된 키워드. */
    private List<String> suggestions(Long account) {
        Map<String, String> names = keywordRepository.findByAdAccountId(account).stream()
                .collect(Collectors.toMap(AdKeyword::getNccKeywordId, AdKeyword::getKeyword, (a, b) -> a));
        if (names.isEmpty()) {
            return List.of();
        }
        LocalDate until = LocalDate.now().minusDays(1);
        List<String> byCost = statRepository.aggregateByKeyword(account, until.minusDays(29), until).stream()
                .sorted(Comparator.comparingLong((KeywordAggregate r) -> r.salesAmt() == null ? 0 : r.salesAmt()).reversed())
                .map(r -> names.get(r.nccKeywordId()))
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .toList();
        return byCost.isEmpty() ? names.values().stream().distinct().sorted().limit(5).toList() : byCost;
    }

    // --- 보조 ---

    private GoalView view(GoalSession session) {
        BusinessModel model = read(session);
        Slot next = model.nextMissing();
        GoalEconomics.Result economics = next == null ? GoalEconomics.compute(model) : null;
        List<Message> messages = messageRepository.findBySessionIdOrderByIdAsc(session.getId()).stream()
                .map(m -> new Message(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
        List<Checkin> checkins = checkinRepository.findByAdAccountIdOrderBySinceDesc(session.getAdAccountId()).stream()
                .map(GoalService::toDto)
                .toList();
        String question = next == null ? null : rules.question(model, next, List.of());
        return new GoalView(session.getId(), session.getStatus(), model, question, economics,
                readAnalysis(session).orElse(null), session.getConfirmedOption(), session.getConfirmedAt(), messages,
                checkins, aiEnabled ? "ai" : "rule");
    }

    private static Checkin toDto(GoalCheckin c) {
        return new Checkin(c.getId(), c.getSince(), c.getUntil(), c.getLeads(), c.getLeadsSource(), c.getDeals(),
                c.getRevenue(), c.getLeads() == 0 ? 0 : round1(c.getDeals() * 100.0 / c.getLeads()));
    }

    private void say(GoalSession session, String role, String content) {
        messageRepository.save(new GoalMessage(session.getAdAccountId(), session.getId(), role, content));
    }

    private BusinessModel read(GoalSession session) {
        try {
            return objectMapper.readValue(session.getModelJson(), BusinessModel.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 사업 구조를 읽지 못했습니다: 세션 " + session.getId(), e);
        }
    }

    private Optional<Analysis> readAnalysis(GoalSession session) {
        if (session.getAnalysisJson() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(session.getAnalysisJson(), Analysis.class));
        } catch (JsonProcessingException e) {
            log.warn("저장된 검토 결과를 읽지 못했습니다: 세션 {} {}", session.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Option find(List<Option> options, String key) {
        return options.stream().filter(o -> o.key().equals(key)).findFirst().orElse(null);
    }

    private static String join(List<String> notes) {
        return String.join("\n", notes);
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static long roundUp(long value, long unit) {
        return (value + unit - 1) / unit * unit;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
