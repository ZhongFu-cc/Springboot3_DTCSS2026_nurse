package tw.org.dtcss.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import cn.dev33.satoken.stp.SaTokenInfo;
import jakarta.validation.constraints.NotBlank;
import tw.org.dtcss.pojo.DTO.AddGroupMemberDTO;
import tw.org.dtcss.pojo.DTO.AddMemberForAdminDTO;
import tw.org.dtcss.pojo.DTO.MemberEmailLogin;
import tw.org.dtcss.pojo.DTO.MemberIdCardLogin;
import tw.org.dtcss.pojo.DTO.MemberLoginDTO;
import tw.org.dtcss.pojo.DTO.WalkInRegistrationDTO;
import tw.org.dtcss.pojo.DTO.addEntityDTO.AddMemberDTO;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutMemberDTO;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutMemberForAdminDTO;
import tw.org.dtcss.pojo.VO.MemberOrderVO;
import tw.org.dtcss.pojo.VO.MemberTagVO;
import tw.org.dtcss.pojo.entity.Attendees;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.entity.Orders;

public interface MemberService extends IService<Member> {

	Member getMember(Long memberId);

	/**
	 * 有中文姓名就使用 <br>
	 * 沒有就組裝英文姓名返回
	 * 
	 * @param member
	 * @return
	 */
	String getOnlyMemberName(Member member);

	/**
	 * mybatis 原始高速查詢所有Member<br>
	 * 輸出Excel數據適用
	 * 
	 * @return
	 */
	List<Member> getMembersEfficiently();

	List<Member> getMemberList();

	List<Member> getMemberListByIds(Collection<Long> memberIds);

	List<Member> getMembersByQuery(String queryText);

	IPage<Member> getMemberPage(Page<Member> page);

	/**
	 * 根據搜尋條件，獲取Member的分頁對象
	 * 
	 * @param page      分頁對象
	 * @param queryText 查詢字串
	 * @param memberIds 限制的memberIds範圍
	 * @return
	 */
	IPage<Member> getMemberPageByQuery(Page<Member> page, String queryText, Collection<Long> memberIds);

	Long getMemberCount();

	Integer getMemberOrderCount(List<Orders> orderList);

	/**
	 * 根據email查詢是否有這個會員
	 * 
	 * @param email
	 * @return
	 */
	Member getMemberByEmail(String email);

	/**
	 * 透過 團體代碼 和 團體角色, 獲得符合的members
	 * 
	 * @param groupCode
	 * @param groupRole
	 * @return
	 */
	List<Member> getMembersByGroupCodeAndRole(String groupCode, String groupRole);

	IPage<MemberOrderVO> getMemberOrderVO(IPage<Orders> orderPage, Integer status, String queryText);

	/**
	 * 獲取尚未付款的會員列表
	 * 
	 * @param page
	 * @param orderList
	 * @param country
	 * @param queryText
	 * @return
	 */
	IPage<MemberTagVO> getUnpaidMemberPage(Page<Member> page, List<Orders> orderList, String country, String queryText);

	/**
	 * 拿到當前團體標籤的index
	 * 
	 * @param groupSize 一組的數量(人數)
	 * @return
	 */
	int getMemberGroupIndex(int groupSize);

	/**
	 * 拿到 某個身分類別 當前團體標籤的index
	 * 
	 * @param groupSize          一組的數量(人數)
	 * @param memberCategoryEnum Enum中的類別
	 * @return
	 */

	/**
	 * 拿到 某個身分類別 當前團體標籤的index
	 * 
	 * @param groupSize
	 * @param memberCategory member.category的值
	 * @return
	 */
	int getMemberCategoryGroupIndex(int groupSize, Integer memberCategory);

	/**
	 * 校驗email是否註冊過<br>
	 * 沒有則,新增會員,並返回會員資料
	 * 
	 * @param addMemberDTO
	 * @return
	 */
	Member addMember(AddMemberDTO addMemberDTO);

	/**
	 * 後台管理者新增會員<br>
	 * 校驗email是否註冊過<br>
	 * 沒有則,新增會員,並返回會員資料
	 * 
	 * @param addMemberForAdminDTO
	 * @return
	 */
	Member addMemberForAdmin(AddMemberForAdminDTO addMemberForAdminDTO);

	/**
	 * 團體報名,新增會員
	 * 
	 * @param groupCode         團體代碼
	 * @param groupRole         團體中的角色
	 * @param addGroupMemberDTO 會員個人資訊
	 * @return
	 */
	Member addMemberByRoleAndGroup(String groupCode, String groupRole, AddGroupMemberDTO addGroupMemberDTO);

	/**
	 * 現場報到時，新增會員
	 * 
	 * @param walkInRegistrationDTO
	 * @return
	 */
	Member addMemberOnSite(WalkInRegistrationDTO walkInRegistrationDTO);

	/**
	 * 會員個人更新基本資料
	 * 
	 * @param putMemberDTO
	 */
	void updateMember(PutMemberDTO putMemberDTO);

	void updateMemberForAdmin(PutMemberForAdminDTO putMemberForAdminDTO);

	void deleteMember(Long memberId);

	void deleteMemberList(List<Long> memberIds);

	Member getMemberInfo();

	/**
	 * 會員登入，用於註冊後立馬登入使用
	 * 
	 * @param memberLoginInfo
	 * @return
	 */
	SaTokenInfo login(Member member);

	/**
	 * 會員登入 - Email & Password
	 * 
	 * @param memberEmailLogin
	 * @return
	 */
	SaTokenInfo login(MemberEmailLogin memberEmailLogin);

	/**
	 * 會員登入 - IdCard & Password
	 * 
	 * @param memberIdCardLogin
	 * @return
	 */
	SaTokenInfo login(MemberIdCardLogin memberIdCardLogin);

	/**
	 * 「外國人」登入 - Email & Password 綁定國籍「非」台灣
	 * 
	 * @param memberLoginDTO
	 * @return
	 */
	SaTokenInfo foreignLogin(MemberLoginDTO memberLoginDTO);

	/**
	 * 「本國人」登入 - IdCard & Password 綁定國籍 台灣
	 * 
	 * @param memberLoginDTO
	 * @return
	 */
	SaTokenInfo localLogin(MemberLoginDTO memberLoginDTO);

	/**
	 * 會員登出
	 */
	void logout();

	/**
	 * 根據memberId，獲取Member並轉換成VO對象
	 * 
	 * @param memberId
	 * @return
	 */
	MemberTagVO getMemberTagVOByMember(Long memberId);

	/**
	 * 根據 memberIds 查詢範圍內, Member 的映射關係
	 * 
	 * @param memberIds
	 * @return 獲得以 memberId為key , Member為value的 Map對象
	 */
	Map<Long, Member> getMemberMapByIds(Collection<Long> memberIds);

	/**
	 * 根據 attendeesList 查詢範圍內, Member 的映射關係
	 * 
	 * @param attendeesList
	 * @return 獲得以 memberId為key , Member為value的 Map對象
	 */
	Map<Long, Member> getMemberMapByAttendeesList(Collection<Attendees> attendeesList);

	/**
	 * 獲取所有會員資料,並產生成Map映射對象
	 * 
	 * @return memberId為key , Member為值得 Map對象
	 */
	Map<Long, Member> getMemberMap();

	/**
	 * 透過email快速登入
	 * 
	 * @param email
	 * @return
	 */
	SaTokenInfo quickLogin(String email);

}
