package tw.org.dtcss.manager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cn.dev33.satoken.stp.SaTokenInfo;
import lombok.RequiredArgsConstructor;
import tw.org.dtcss.config.RegistrationFeeConfig;
import tw.org.dtcss.constants.I18nMessageKey;
import tw.org.dtcss.context.ProjectModeContext;
import tw.org.dtcss.enums.GroupRegistrationEnum;
import tw.org.dtcss.enums.MemberCategoryEnum;
import tw.org.dtcss.enums.RegistrationPhaseEnum;
import tw.org.dtcss.exception.RegistrationClosedException;
import tw.org.dtcss.helper.MessageHelper;
import tw.org.dtcss.helper.TagAssignmentHelper;
import tw.org.dtcss.pojo.DTO.AddGroupMemberDTO;
import tw.org.dtcss.pojo.DTO.AddMemberForAdminDTO;
import tw.org.dtcss.pojo.DTO.GroupRegistrationDTO;
import tw.org.dtcss.pojo.DTO.addEntityDTO.AddMemberDTO;
import tw.org.dtcss.pojo.entity.Attendees;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.service.AttendeesService;
import tw.org.dtcss.service.AttendeesTagService;
import tw.org.dtcss.service.InvitedSpeakerService;
import tw.org.dtcss.service.MemberService;
import tw.org.dtcss.service.MemberTagService;
import tw.org.dtcss.service.OrdersService;
import tw.org.dtcss.service.SettingService;
import tw.org.dtcss.service.TagService;
import tw.org.dtcss.utils.CountryUtil;

@Component
@RequiredArgsConstructor
public class MemberRegistrationManager {

	@Value("${project.name}")
	private String PROJECT_NAME;

	@Value("${project.banner-url}")
	private String BANNER_PHOTO_URL;

	// 團體折扣 , 從application.yml 進行修改 
	@Value("${project.group-discount}")
	private Double GROUP_DISCOUNT;


	private final RegistrationFeeConfig registrationFeeConfig;

	private final ProjectModeContext projectModeContext;

	private final MessageHelper messageHelper;
	private final TagAssignmentHelper tagAssignmentHelper;
	private final MemberService memberService;
	private final OrdersService ordersService;
	private final AttendeesService attendeesService;
	private final TagService tagService;
	private final MemberTagService memberTagService;
	private final AttendeesTagService attendeesTagService;
	private final SettingService settingService;
	private final InvitedSpeakerService invitedSpeakerService;

	/**
	 * 註冊功能,新增會員,產生「付費」訂單
	 * 
	 * @param addMemberDTO
	 * @return
	 */
	@Transactional
	public SaTokenInfo addMember(AddMemberDTO addMemberDTO) {

		// 1.先判斷是否處於註冊時間內
		if (!settingService.isRegistrationOpen()) {
			throw new RegistrationClosedException(messageHelper.get(I18nMessageKey.Registration.CLOSED));
		}

		// 2.新增會員
		Member member = memberService.addMember(addMemberDTO);
		System.out.println(member.getEmail() + "--------------------------------Email");

		// 3.以當前模式策略,執行註冊流程 (計算金額=>產生訂單=>產生通知信並寄出)
		projectModeContext.getStrategy().handleRegistration(member);
		System.out.println("信件已寄出");
		// 4.獲取當下Member群體的Index,進行會員標籤分組
		tagAssignmentHelper.assignTag(member.getMemberId(), memberService::getMemberGroupIndex,
				tagService::getOrCreateMemberGroupTag, memberTagService::addMemberTag);

		// 5.獲取當下Member Category群體的Index,進行會員身份標籤分組
		tagAssignmentHelper.assignMemberCategoryTag(member.getMemberId(),
				MemberCategoryEnum.fromValue(member.getCategory()), memberService::getMemberCategoryGroupIndex,
				tagService::getOrCreateMemberCategoryGroupTag, memberTagService::addMemberTag);

		// 6.返回token , 讓用戶於註冊後登入
		return memberService.login(member);
	}

	/**
	 * 團體報名 註冊功能,新增會員,產生「付費」訂單
	 * 
	 * @param groupRegistrationDTO
	 */
	@Transactional
	public void addGroupMember(GroupRegistrationDTO groupRegistrationDTO) {

		// 1.先判斷是否處於 團體報名 註冊時間內
		if (!settingService.isGroupRegistrationOpen()) {
			throw new RegistrationClosedException(messageHelper.get(I18nMessageKey.Registration.Group.CLOSED));
		}

		// 2.拿到配置設定,知道處於哪個註冊階段
		RegistrationPhaseEnum registrationPhaseEnum = settingService.getRegistrationPhaseEnum();

		// 3.在外部直接產生團體的代號
		String groupCode = UUID.randomUUID().toString();

		// 4.提取團體報名的所有人，方便後續調用
		List<AddGroupMemberDTO> groupMembers = groupRegistrationDTO.getGroupMembers();

		// 5.計算所有成員的費用總和，折扣後的金額總額(9折
		BigDecimal discountedTotalFee = groupMembers.stream()
				.map(m -> registrationFeeConfig.getFee(registrationPhaseEnum.getValue(),
						CountryUtil.getTaiwanOrForeign(m.getCountry()),
						MemberCategoryEnum.fromValue(m.getCategory()).getConfigKey()))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.multiply(BigDecimal.valueOf(GROUP_DISCOUNT));

		// 6.團體報名有複數會員,遍歷進行新增
		for (int i = 0; i < groupMembers.size(); i++) {

			// 6-1獲取當前團體報名對象
			AddGroupMemberDTO addGroupMemberDTO = groupMembers.get(i);
			boolean isMaster = i == 0;

			// 6-2新增會員
			Member member = memberService.addMemberByRoleAndGroup(groupCode,
					isMaster ? GroupRegistrationEnum.MASTER.getValue() : GroupRegistrationEnum.SLAVE.getValue(),
					addGroupMemberDTO);

			// 6-3以當前模式,去執行團體報名的策略
			projectModeContext.getStrategy().handleGroupRegistration(member, isMaster, discountedTotalFee);

			// 6-4.獲取當下Member群體的Index,進行會員標籤分組
			tagAssignmentHelper.assignTag(member.getMemberId(), memberService::getMemberGroupIndex,
					tagService::getOrCreateMemberGroupTag, memberTagService::addMemberTag);

			// 6-5.獲取當下Member Category群體的Index,進行會員身份標籤分組
			tagAssignmentHelper.assignMemberCategoryTag(member.getMemberId(),
					MemberCategoryEnum.fromValue(member.getCategory()), memberService::getMemberCategoryGroupIndex,
					tagService::getOrCreateMemberCategoryGroupTag, memberTagService::addMemberTag);

			
		}

	}

	/**
	 * 後台新增會員功能,產生「免費」訂單
	 * 
	 * @param addMemberForAdminDTO
	 */
	@Transactional
	public void addMemberForAdmin(AddMemberForAdminDTO addMemberForAdminDTO) {

		// 1.判斷Email是否被註冊，如果沒有新增會員
		Member member = memberService.addMemberForAdmin(addMemberForAdminDTO);

		// 2.新增「免費」的訂單,並標註 「已付款」
		ordersService.createFreeRegistrationOrder(member);

		// 3.獲取當下Member群體的Index,進行會員標籤分組
		tagAssignmentHelper.assignTag(member.getMemberId(), memberService::getMemberGroupIndex,
				tagService::getOrCreateMemberGroupTag, memberTagService::addMemberTag);
		
		// 4.獲取當下Member Category群體的Index,進行會員身份標籤分組
		tagAssignmentHelper.assignMemberCategoryTag(member.getMemberId(),
				MemberCategoryEnum.fromValue(member.getCategory()), memberService::getMemberCategoryGroupIndex,
				tagService::getOrCreateMemberCategoryGroupTag, memberTagService::addMemberTag);

		// 5.由後台新增的Member , 自動付款完成，新增進與會者名單
		Attendees attendees = attendeesService.addAttendees(member);

		// 6.獲取當下與會者群體的Index,進行與會者標籤分組
		tagAssignmentHelper.assignTag(attendees.getAttendeesId(), attendeesService::getAttendeesGroupIndex,
				tagService::getOrCreateAttendeesGroupTag, attendeesTagService::addAttendeesTag);
		
		// 7.如果是講者身分,則新增到invited-speaker, 這個也再考慮, 可能違反SRP
		if (MemberCategoryEnum.SPEAKER.getValue().equals(member.getCategory())) {
			invitedSpeakerService.addInviredSpeaker(member);
		}

	}

}
