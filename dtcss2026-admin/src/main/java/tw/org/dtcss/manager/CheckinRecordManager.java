package tw.org.dtcss.manager;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tw.org.dtcss.convert.AttendeesConvert;
import tw.org.dtcss.convert.CheckinRecordConvert;
import tw.org.dtcss.enums.CheckinActionTypeEnum;
import tw.org.dtcss.exception.CheckinRecordException;
import tw.org.dtcss.handler.AttendeesVOHandler;
import tw.org.dtcss.pojo.DTO.addEntityDTO.AddCheckinRecordDTO;
import tw.org.dtcss.pojo.VO.AttendeesVO;
import tw.org.dtcss.pojo.VO.CheckinRecordVO;
import tw.org.dtcss.pojo.entity.Attendees;
import tw.org.dtcss.pojo.entity.CheckinRecord;
import tw.org.dtcss.pojo.entity.Form;
import tw.org.dtcss.pojo.entity.FormResponse;
import tw.org.dtcss.pojo.entity.Member;
import tw.org.dtcss.pojo.excelPojo.AttendeesExcel;
import tw.org.dtcss.pojo.excelPojo.CheckinRecordExcel;
import tw.org.dtcss.service.AttendeesService;
import tw.org.dtcss.service.CheckinRecordService;
import tw.org.dtcss.service.FormResponseService;
import tw.org.dtcss.service.FormService;
import tw.org.dtcss.service.MemberService;

@Component
@RequiredArgsConstructor
public class CheckinRecordManager {

	private final MemberService memberService;
	private final CheckinRecordService checkinRecordService;
	private final CheckinRecordConvert checkinRecordConvert;
	private final AttendeesService attendeesService;
	private final AttendeesConvert attendeesConvert;
	private final AttendeesVOHandler attendeesVOHandler;
	private final FormService formService;
	private final FormResponseService formResponseService;

	/**
	 * 獲得此筆簽到退資料 及 簽到者身分
	 * 
	 * @param checkinRecordId
	 * @return
	 */
	public CheckinRecordVO getCheckinRecordVO(Long checkinRecordId) {

		// 1.獲取這筆簽到記錄
		CheckinRecord checkinRecord = checkinRecordService.getCheckinRecord(checkinRecordId);

		// 2.查詢此簽到者的基本資訊
		AttendeesVO attendeesVO = attendeesVOHandler.getAttendeesVO(checkinRecord.getAttendeesId());

		// 3.實體類轉換成VO
		CheckinRecordVO checkinRecordVO = checkinRecordConvert.entityToVO(checkinRecord);

		// 4.vo中填入與會者VO對象  2025/9/24 重構臨時註解
		checkinRecordVO.setAttendeesVO(attendeesVO);

		return checkinRecordVO;
	}

	/**
	 * 轉換 簽到/退紀錄,並補上簽到者資料
	 * 
	 * @param checkinRecordList
	 * @return
	 */
	private List<CheckinRecordVO> convertToCheckinRecordVOList(List<CheckinRecord> checkinRecordList) {

		// 1.獲取與會者的ID(去重)
		Set<Long> attendeesIdSet = checkinRecordList.stream()
				.map(CheckinRecord::getAttendeesId)
				.collect(Collectors.toSet());

		// 2.透過去重的與會者ID拿到資料
		List<AttendeesVO> attendeesVOList = attendeesVOHandler.getAttendeesVOsByAttendeesIds(attendeesIdSet);

		// 3.做成資料映射attendeesID 對應 AttendeesVO
		Map<Long, AttendeesVO> AttendeesVOMap = attendeesVOList.stream()
				.collect(Collectors.toMap(AttendeesVO::getAttendeesId, Function.identity()));

		// 4.checkinRecordList stream轉換後映射組裝成VO對象
		List<CheckinRecordVO> checkinRecordVOList = checkinRecordList.stream().map(checkinRecord -> {
			CheckinRecordVO vo = checkinRecordConvert.entityToVO(checkinRecord);
			vo.setAttendeesVO(AttendeesVOMap.get(checkinRecord.getAttendeesId()));
			return vo;
		}).collect(Collectors.toList());

		return checkinRecordVOList;
	}

	/**
	 * 獲取CheckinRecordVO 列表
	 * 
	 * @return
	 */
	public List<CheckinRecordVO> getCheckinRecordVOList() {

		// 1.獲取所有簽到/退紀錄
		List<CheckinRecord> checkinRecordList = checkinRecordService.getCheckinRecordList();

		// 2.使用私有方法獲取CheckinRecordVOList
		List<CheckinRecordVO> checkinRecordVOList = this.convertToCheckinRecordVOList(checkinRecordList);

		return checkinRecordVOList;
	}

	/**
	 * 獲取CheckinRecordVO 分頁對象
	 * 
	 * @param page
	 * @return
	 */
	public IPage<CheckinRecordVO> getCheckinRecordVOPage(Page<CheckinRecord> page) {
		// 1.獲取簽到記錄分頁對象
		IPage<CheckinRecord> checkinRecordPage = checkinRecordService.getCheckinRecordPage(page);

		// 2.轉換資料拿到CheckinRecordVO對向
		List<CheckinRecordVO> checkinRecordVOList = this.convertToCheckinRecordVOList(checkinRecordPage.getRecords());

		// 3.封裝成VOpage
		Page<CheckinRecordVO> checkinRecordVOPage = new Page<>(checkinRecordPage.getCurrent(),
				checkinRecordPage.getSize(), checkinRecordPage.getTotal());
		checkinRecordVOPage.setRecords(checkinRecordVOList);

		return checkinRecordVOPage;
	}

	/**
	 * 新增簽到記錄
	 * 
	 * @param addCheckinRecordDTO
	 * @return
	 */
	public CheckinRecordVO addCheckinRecord(AddCheckinRecordDTO addCheckinRecordDTO) {

		//拿到memberId
		Attendees attendee = attendeesService.getAttendees(addCheckinRecordDTO.getAttendeesId());

		// 如果本次為簽退操作
		if (CheckinActionTypeEnum.CHECKOUT.getValue().equals(addCheckinRecordDTO.getActionType())) {

			// 獲取簽退表單
			Form checkoutForm = formService.getCheckoutForm();
			if (checkoutForm != null) {
				// 查看此與會者有沒有填寫簽退表單，沒有填寫過則拋出錯誤
				List<FormResponse> formResponses = formResponseService
						.searchSubmissionsByMember(checkoutForm.getFormId(), attendee.getMemberId());
				if (formResponses.isEmpty()) {
					throw new CheckinRecordException("未填寫會後表單，請先進行填寫");
				}
			}

		}

		// 新增簽到/退紀錄
		CheckinRecord checkinRecord = checkinRecordService.addCheckinRecord(addCheckinRecordDTO);

		// 組裝VO對象並返回
		return this.getCheckinRecordVO(checkinRecord.getCheckinRecordId());
	}

	/**
	 * 下載所有簽到/退紀錄
	 * 
	 * @param response
	 * @throws IOException
	 */
	public void downloadExcel(HttpServletResponse response) throws IOException {

		// 1.初始設定
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setCharacterEncoding("utf-8");
		// 这里URLEncoder.encode可以防止中文乱码 ， 和easyexcel没有关系
		String fileName = URLEncoder.encode("簽到退紀錄名單", "UTF-8").replaceAll("\\+", "%20");
		response.setHeader("Content-disposition", "attachment;filename*=" + fileName + ".xlsx");

		// 2.高效獲取所有簽到/退資料
		List<CheckinRecord> checkinRecordList = checkinRecordService.getCheckinRecordsEfficiently();

		// 3.高效獲取所有會員資料映射
		Map<Long, Member> memberMap = memberService.getMemberMap();

		// 4.高效獲取所有與會者資料映射
		Map<Long, Attendees> attendeesMap = attendeesService.getAttendeesMap();

		// 資料轉換成Excel
		List<CheckinRecordExcel> excelData = checkinRecordList.stream().map(checkinRecord -> {
			// 透過attendeesId先拿到attendeesVO
			AttendeesVO attendeesVO = attendeesConvert.entityToVO(attendeesMap.get(checkinRecord.getAttendeesId()));
			// 再透過 memberId放入Member
			attendeesVO.setMember(memberMap.get(attendeesVO.getMemberId()));
			// 獲取到AttendeesExcel 再轉換成 CheckinRecordExcel
			AttendeesExcel attendeesExcel = attendeesConvert.voToExcel(attendeesVO);
			CheckinRecordExcel checkinRecordExcel = checkinRecordConvert
					.attendeesExcelToCheckinRecordExcel(attendeesExcel);

			//最後再補上缺失的屬性
			checkinRecordExcel.setActionTime(checkinRecord.getActionTime());
			checkinRecordExcel.setActionType(CheckinActionTypeEnum.fromValue(checkinRecord.getActionType()).getLabel());
			checkinRecordExcel.setLocation(checkinRecord.getLocation());
			checkinRecordExcel.setCheckinRecordId(checkinRecord.getCheckinRecordId().toString());
			checkinRecordExcel.setRemark(checkinRecord.getRemark());
			return checkinRecordExcel;

		}).collect(Collectors.toList());

		EasyExcel.write(response.getOutputStream(), CheckinRecordExcel.class).sheet("簽到退紀錄列表").doWrite(excelData);

	}

}
