package tw.org.dtcss.manager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ecpay.payment.integration.AllInOne;
import ecpay.payment.integration.domain.AioCheckOutOneTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tw.org.dtcss.constants.I18nMessageKey;
import tw.org.dtcss.enums.ECpayRtnCodeEnum;
import tw.org.dtcss.enums.GroupRegistrationEnum;
import tw.org.dtcss.enums.OrderStatusEnum;
import tw.org.dtcss.enums.TagTypeEnum;
import tw.org.dtcss.exception.OrderPaymentException;
import tw.org.dtcss.exception.RegistrationClosedException;
import tw.org.dtcss.helper.MessageHelper;
import tw.org.dtcss.helper.TagAssignmentHelper;
import tw.org.dtcss.pojo.DTO.ECPayDTO.ECPayResponseDTO;
import tw.org.dtcss.pojo.entity.Attendees;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.entity.Orders;
import tw.org.dtcss.pojo.entity.Payment;
import tw.org.dtcss.service.AttendeesService;
import tw.org.dtcss.service.AttendeesTagService;
import tw.org.dtcss.service.MemberService;
import tw.org.dtcss.service.MemberTagService;
import tw.org.dtcss.service.OrdersService;
import tw.org.dtcss.service.PaymentService;
import tw.org.dtcss.service.SettingService;
import tw.org.dtcss.service.TagService;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentManager {

	@Value("${project.payment.client-back-url}")
	private String CLIENT_BACK_URL;

	@Value("${project.payment.return-url}")
	private String RETURN_URL;

	@Value("${project.payment.prefix}")
	private String PREFIX;

	private final MessageHelper messageHelper;
	private final TagAssignmentHelper tagAssignmentHelper;
	private final MemberService memberService;
	private final MemberTagService memberTagService;
	private final OrdersService ordersService;
	private final PaymentService paymentService;
	private final AttendeesService attendeesService;
	private final AttendeesTagService attendeesTagService;
	private final TagService tagService;
	private final SettingService settingService;

	private static final AtomicInteger SEQ = new AtomicInteger(0);
	private static volatile long lastMillis = -1L;

	/**
	 * 使用 project.payment.prefix + <br>
	 * 時間戳轉Base36 ,減少長度 + <br>
	 * 同毫秒內的三位數序列號 <br>
	 * 產生廠商訂單編號
	 * 
	 * @return
	 */
	private String generateTradeNo() {

		// 1.拿到配置文件的payment 前墜
		String prefix = PREFIX;

		// 2.prefix 最多 9 碼 
		if (prefix.length() > 9) {
			prefix = prefix.substring(0, 9);
		}

		// 3.拿到當下毫秒級的時間戳
		long now = System.currentTimeMillis();

		// 4.同毫秒內 sequence 控制
		if (now == lastMillis) {
			int seq = SEQ.incrementAndGet();
			if (seq >= 1000) {
				// 同毫秒超過999則交易等待下一毫秒
				while (System.currentTimeMillis() == now) {
					// 讓出 CPU，允許 JVM 排程其他 thread 執行
					Thread.yield();
				}
				now = System.currentTimeMillis();
				SEQ.set(0);
			}
		} else {
			SEQ.set(0);
			lastMillis = now;
		}

		// 5.Base36 壓縮時間戳
		String base36Time = Long.toString(now, 36).toUpperCase();

		// 6.格式化 3位數的 sequence , 
		String seqPart = String.format("%03d", SEQ.get());

		// 7.組裝TradeNo
		System.out.println(prefix + base36Time + seqPart);

		return prefix + base36Time + seqPart;

	}

	/**
	 * 產生付款頁面
	 * 
	 * @param orderId
	 * @return
	 */
	public String generatePaymentPage(Long orderId) {

		// 獲取當前時間
		LocalDateTime now = LocalDateTime.now();

		// 先判斷是否處於可註冊時間，當超出註冊時間直接拋出異常，讓全局異常去處理
		if (!settingService.isRegistrationOpen()) {
			throw new RegistrationClosedException(messageHelper.get(I18nMessageKey.Payment.CLOSED));
		}

		// 1.創建綠界全方位金流對象
		AllInOne allInOne = new AllInOne("");

		// 2.創建信用卡一次付清模式
		AioCheckOutOneTime aioCheckOutOneTime = new AioCheckOutOneTime();

		// 3.根據前端傳來的資料,獲取訂單
		Orders order = ordersService.getOrders(orderId);

		// 4.根據訂單ID,獲取這個訂單的持有者Member，如果訂單為子報名者要求產生，則直接拋出錯誤
		Member member = memberService.getMember(order.getMemberId());
		if (GroupRegistrationEnum.SLAVE.getValue().equals(member.getGroupRole())) {
			throw new OrderPaymentException(messageHelper.get(I18nMessageKey.Payment.Group.MUST_BE_PRIMARY));
		}
		
		// 如果訂單已經付款也拋出錯誤，訂單已繳費
		if(OrderStatusEnum.PAYMENT_SUCCESS.getValue().equals(order.getStatus())) {
			throw new OrderPaymentException("付款已完成，請勿重複付款");
		}

		// 5.獲取當前時間並格式化，為了填充交易時間
		String nowFormat = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));

		// 訂單交易編號,僅接受20位長度，編號不可重複，使用自定義生成function 處理
		aioCheckOutOneTime.setMerchantTradeNo(this.generateTradeNo());
		// 設定交易日期
		aioCheckOutOneTime.setMerchantTradeDate(nowFormat);
		// 綠界金流 僅接受新台幣 以及整數的金額，所以BigDecimal 要進行去掉無意義的0以及轉換成String
		aioCheckOutOneTime.setTotalAmount(order.getTotalAmount().stripTrailingZeros().toPlainString());
		// 設定交易描述
		aioCheckOutOneTime.setTradeDesc(
				"This payment page only displays the total order amount. For details, please see the website membership page");
		// 設定交易產品名稱概要,他沒有辦法一個item對應一個amount , 但可以透過#將item分段顯示
		// 例如: item01#item02#item03
		aioCheckOutOneTime.setItemName(order.getItemsSummary());
		// 設定付款完成後，返回的前端網址，這邊讓他回到官網
		aioCheckOutOneTime.setClientBackURL(CLIENT_BACK_URL);
		// 設定付款完成通知的網址,應該可以直接設定成後端API，實證有效
		aioCheckOutOneTime.setReturnURL(RETURN_URL);
		// 這邊不需要他回傳額外付款資料
		aioCheckOutOneTime.setNeedExtraPaidInfo("N");
		// 設定英文介面，不特別設定為 繁體中文
		aioCheckOutOneTime.setLanguage("ENG");
		// 這邊使用他預留的客製化欄位,填入我們的訂單ID,當他透過return URL 觸發我們API時會回傳
		// 這邊因為還是只能String , 所以要將Long 類型做轉換
		aioCheckOutOneTime.setCustomField1(String.valueOf(order.getOrdersId()));

		// 6.前述設定完成,放入全方位金流對象
		String form = allInOne.aioCheckOut(aioCheckOutOneTime, null);
		System.out.println("產生的form " + form);
		return form;

	}

	/**
	 * 處理綠界的回調資訊
	 * 
	 * @param ECPayResponseDTO
	 */
	@Transactional
	public void handleEcpayCallback(ECPayResponseDTO ECPayResponseDTO) {

		// 1.新增此筆交易明細
		Payment payment = paymentService.addPayment(ECPayResponseDTO);

		// 2.獲取此筆交易的訂單
		Orders currentOrders = ordersService.getOrders(payment.getOrdersId());

		// 3.查詢此訂單的會員
		Member member = memberService.getMember(currentOrders.getMemberId());

		// 4.付款成功，更新訂單的付款狀態
		if (ECpayRtnCodeEnum.SUCCESS.getCode().equals(payment.getRtnCode())) {

			// 如果當前訂單狀態不是 '付款成功' 則變更狀態
			if (!currentOrders.getStatus().equals(OrderStatusEnum.PAYMENT_SUCCESS.getValue())) {
				// 4-1更新這筆訂單資料
				currentOrders.setStatus(OrderStatusEnum.PAYMENT_SUCCESS.getValue());
				ordersService.updateById(currentOrders);
				log.info(currentOrders.getOrdersId() + " 付款成功，更新資料狀態");

				// 4-2 付款完成，所以將他新增進 與會者名單
				Attendees attendees = attendeesService.addAttendees(member);

				// 4-3.獲取當下與會者群體的Index,進行與會者標籤分組
				tagAssignmentHelper.assignTag(attendees.getAttendeesId(), attendeesService::getAttendeesGroupIndex,
						tagService::getOrCreateAttendeesGroupTag, attendeesTagService::addAttendeesTag);

				// 4-4.移除會員 註冊費未付款 Tag
				tagAssignmentHelper.removeGroupTagsByPattern(member.getMemberId(), TagTypeEnum.MEMBER.getType(),
						"註冊費未付款", tagService::getTagIdsByTypeAndNamePattern, memberTagService::removeTagsFromMember);

			}

			// 5.付款失敗，更新訂單的付款狀態
		} else {
			// 如果已經成功過就不用在更新成失敗
			if (!currentOrders.getStatus().equals(OrderStatusEnum.PAYMENT_SUCCESS.getValue())) {
				// 5-1付款失敗，並更新這筆訂單資料
				currentOrders.setStatus(OrderStatusEnum.PAYMENT_FAILED.getValue());
				ordersService.updateById(currentOrders);
				log.warn(currentOrders.getOrdersId() + " 付款失敗，更新資料狀態");
			}
		}

		// 5.判斷這個member有沒有group code，且付款的更新者為master，從如果有才進行此方法塊
		if (member.getGroupCode() != null && GroupRegistrationEnum.MASTER.getValue().equals(member.getGroupRole())) {

			// 5-1 拿到所屬同一個團體報名的會員名單，並且是要group_role 為 slave的成員
			List<Member> groupMemberList = memberService.getMembersByGroupCodeAndRole(member.getGroupCode(),
					GroupRegistrationEnum.SLAVE.getValue());

			// 5-2 遍歷去更新order 還有 添加與會者身分
			for (Member slaveMember : groupMemberList) {
				// 5-3 找到memberId為名單內成員且訂單的itemsSummary 為 註冊費的訂單，同步更新
				ordersService.syncSlaveMemberOrderStatus(slaveMember.getMemberId(), currentOrders.getStatus());

				// 如果付款完成，將報名者添加到attendees 表裡面，代表他已具備入場資格
				if (OrderStatusEnum.PAYMENT_SUCCESS.getValue().equals(currentOrders.getStatus())) {
					// 4-2 付款完成，所以將他新增進 與會者名單
					Attendees attendees = attendeesService.addAttendees(slaveMember);
					// 4-3.獲取當下與會者群體的Index,進行與會者標籤分組
					tagAssignmentHelper.assignTag(attendees.getAttendeesId(), attendeesService::getAttendeesGroupIndex,
							tagService::getOrCreateAttendeesGroupTag, attendeesTagService::addAttendeesTag);

					// 4-4.移除會員 註冊費未付款 Tag
					tagAssignmentHelper.removeGroupTagsByPattern(slaveMember.getMemberId(), TagTypeEnum.MEMBER.getType(),
							"註冊費未付款", tagService::getTagIdsByTypeAndNamePattern, memberTagService::removeTagsFromMember);

					
				}

			}

		}

	}

}
