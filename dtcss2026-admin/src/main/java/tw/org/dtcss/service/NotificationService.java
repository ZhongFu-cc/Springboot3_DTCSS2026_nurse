package tw.org.dtcss.service;

import tw.org.dtcss.pojo.DTO.EmailBodyContent;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.entity.Orders;
import tw.org.dtcss.pojo.entity.Paper;

public interface NotificationService {

	/**
	 * 生成註冊成功的通知信件內容
	 * 
	 * @param member
	 * @param bannerPhotoUrl
	 * @return
	 */
	EmailBodyContent generateRegistrationSuccessContent(Member member, String bannerPhotoUrl,Orders registrationOrder);
	
	/**
	 * 生成 團體報名 註冊成功的通知信件內容
	 * 
	 * @param member
	 * @param bannerPhotoUrl
	 * @return
	 */
	EmailBodyContent generateGroupRegistrationSuccessContent(Member member, String bannerPhotoUrl);
	
	/**
	 * 生成 找回密碼 的通知信件內容
	 * 
	 * @param password
	 * @return
	 */
	EmailBodyContent generateRetrieveContent(String password);
	

	/**
	 * 生成 投稿成功 的通知信件內容
	 * 
	 * @param paper
	 * @return
	 */
	EmailBodyContent generateAbstractSuccessContent(Paper paper);
	
	/**
	 * 生成講者更新CV 或 照片時的通知信件內容
	 * 
	 * @param speakerName
	 * @param adminDashboardUrl
	 * @return
	 */
	EmailBodyContent generateSpeakerUpdateContent(String speakerName, String adminDashboardUrl);

	/**
	 * 生成現場註冊後的通知信
	 * 
	 * @param attendeesId
	 * @param bannerPhotoUrl
	 * @return
	 */
	EmailBodyContent generateWalkInRegistrationContent(Long attendeesId, String bannerPhotoUrl);
	
}
