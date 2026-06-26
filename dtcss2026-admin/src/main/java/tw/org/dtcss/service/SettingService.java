package tw.org.dtcss.service;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.extension.service.IService;

import tw.org.dtcss.enums.RegistrationPhaseEnum;
import tw.org.dtcss.exception.SettingException;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutSettingDTO;
import tw.org.dtcss.pojo.VO.SettingVO;
import tw.org.dtcss.pojo.entity.Setting;

public interface SettingService extends IService<Setting> {

	/**
	 * 取得唯一的系統設定紀錄。
	 *
	 * @return 系統設定實體 (Setting)。
	 */
	Setting getSetting();

	/**
	 * 更新唯一的系統設定紀錄。
	 *
	 * @param putSettingDTO 包含更新設定資訊的資料傳輸物件。
	 * @throws SettingException 如果系統設定紀錄不存在，則拋出此異常。
	 */
	void updateSetting(PutSettingDTO putSettingDTO);

	/**
	 * 判斷當前時間屬於早鳥優惠的哪一個階段。
	 *
	 * @return 返回表示早鳥階段的枚舉。
	 *         如果當前時間不在任何早鳥階段或設置不完整，則返回 "NONE"。
	 */
	RegistrationPhaseEnum getRegistrationPhaseEnum();

	/**
	 * 判斷 指定時間 屬於早鳥優惠的哪一個階段。
	 *
	 * @return 返回表示早鳥階段的枚舉。
	 *         如果當前時間不在任何早鳥階段或設置不完整，則返回 "NONE"。
	 */
	RegistrationPhaseEnum getRegistrationPhaseEnum(LocalDateTime targetDateTime);

	/**
	 * 檢查是否仍可下訂單 (例如：訂房或城市觀光)。
	 * 判斷依據為當前時間是否在最後下訂單時間之前或等於最後下訂單時間。
	 *
	 * @return 如果可以下訂單則返回 true，否則返回 false。
	 * @throws SettingException 如果訂單相關的設定時間未設定，則拋出此異常。
	 */
	Boolean canPlaceOrder();

	/**
	 * 檢查註冊功能目前是否開放。<br>
	 * 判斷依據為當前時間是否在最後註冊時間之前或等於最後註冊時間。<br>
	 * 兼容活動日的註冊
	 *
	 * @return 如果註冊開放則返回 true，否則返回 false。
	 * @throws SettingException 如果註冊相關的設定時間未設定，則拋出此異常。
	 */
	Boolean isRegistrationOpen();

	/**
	 * 檢查 團體報名 註冊功能目前是否開放。
	 * 判斷依據為當前時間是否在最後註冊時間之前或等於最後註冊時間。
	 *
	 * @return 如果註冊開放則返回 true，否則返回 false。
	 * @throws SettingException 如果註冊相關的設定時間未設定，則拋出此異常。
	 */
	Boolean isGroupRegistrationOpen();

	/**
	 * 檢查摘要投稿功能目前是否開放。
	 * 判斷依據為當前時間是否介於摘要開放投稿時間與截止時間之間 (包含起始時間，不包含截止時間)。
	 *
	 * @return 如果摘要投稿開放則返回 true，否則返回 false。
	 * @throws SettingException 如果摘要投稿相關的設定時間不完整，則拋出此異常。
	 */
	Boolean isAbstractSubmissionOpen();

	/**
	 * 檢查 Slide 上傳功能目前是否開放。
	 * 判斷依據為當前時間是否介於 Slide 開放上傳時間與截止時間之間 (包含起始時間，不包含截止時間)。
	 *
	 * @return 如果 Slide 上傳開放則返回 true，否則返回 false。
	 * @throws SettingException 如果 Slide 上傳相關的設定時間不完整，則拋出此異常。
	 */
	Boolean isSlideUploadOpen();

	/**
	 * 是否處於活動日期間<br>
	 * eventStart ~ eventEnd
	 * 
	 * @return
	 */
	Boolean isDuringEventPeriod();

	SettingVO getFrontSetting();
}
