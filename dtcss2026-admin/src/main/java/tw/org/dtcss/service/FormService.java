package tw.org.dtcss.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import tw.org.dtcss.enums.FormStatusEnum;
import tw.org.dtcss.pojo.DTO.addEntityDTO.AddFormDTO;
import tw.org.dtcss.pojo.DTO.putEntityDTO.PutFormDTO;
import tw.org.dtcss.pojo.entity.Form;

/**
 * <p>
 * 自定義客制化表單 服务类
 * </p>
 *
 * @author Joey
 * @since 2025-12-23
 */
public interface FormService extends IService<Form> {

	Form searchForm(Long formId);

	/**
	 * 獲取簽退表單
	 * 
	 * @return
	 */
	Form getCheckoutForm();

	/**
	 * 判斷目前是否有綁定簽退表單,已存在DB中<br>
	 * 這邊會排除這次傳入的表單本身
	 * 
	 * @return
	 */
	boolean existCheckoutFormInDB(Form form);

	/**
	 * 傳入當前表單 , 只有當 當前表單是否有要綁定簽退表單 以及 簽退表單已是否存在 的情況會返回true
	 * 
	 * @param form 當前要新增 或 修改的表單
	 * @return
	 */
	boolean existCheckoutForm(Form form);

	/**
	 * 根據表單狀態 及 查詢條件 , 獲取表單分頁
	 * 
	 * @param page           分頁對象
	 * @param formStatusEnum 發布狀態
	 * @param queryText      查詢文字
	 * @return
	 */
	IPage<Form> searchFormPageByQuery(Page<Form> page, FormStatusEnum formStatusEnum, String queryText);

	Form create(AddFormDTO addForm);

	void modify(PutFormDTO putFormDTO);

}
