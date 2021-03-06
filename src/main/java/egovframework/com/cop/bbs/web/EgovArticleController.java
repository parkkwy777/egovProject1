package egovframework.com.cop.bbs.web;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springmodules.validation.commons.DefaultBeanValidator;

import com.sun.star.sdbc.SQLException;

import egovframework.com.cmm.EgovMessageSource;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.service.EgovFileMngService;
import egovframework.com.cmm.service.EgovFileMngUtil;
import egovframework.com.cmm.service.FileVO;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import egovframework.com.cmm.util.EgovXssChecker;
import egovframework.com.cop.bbs.service.BlogVO;
import egovframework.com.cop.bbs.service.Board;
import egovframework.com.cop.bbs.service.BoardMaster;
import egovframework.com.cop.bbs.service.BoardMasterVO;
import egovframework.com.cop.bbs.service.BoardVO;
import egovframework.com.cop.bbs.service.EgovArticleService;
import egovframework.com.cop.bbs.service.EgovBBSMasterService;
import egovframework.com.cop.cmt.service.CommentVO;
import egovframework.com.cop.cmt.service.EgovArticleCommentService;
import egovframework.rte.fdl.property.EgovPropertyService;
import egovframework.rte.fdl.string.EgovStringUtil;
import egovframework.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

/**
 * 게시물 관리를 위한 컨트롤러 클래스
 * @author 공통서비스개발팀 이삼섭
 * @since 2009.06.01
 * @version 1.0
 * @see
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *   
 *   수정일      수정자           수정내용
 *  -------       --------    ---------------------------
 *   2009.3.19  이삼섭          최초 생성
 *   2009.06.29	한성곤			2단계 기능 추가 (댓글관리, 만족도조사)
 *   2011.07.01 안민정		 	댓글, 스크랩, 만족도 조사 기능의 종속성 제거
 *   2011.8.26	정진오			IncludedInfo annotation 추가
 *   2011.09.07 서준식           유효 게시판 게시일 지나도 게시물이 조회되던 오류 수정
 *   2016.06.13 김연호			표준프레임워크 3.6 개선
 * </pre>
 */

@Controller
public class EgovArticleController {

	private static final Logger LOGGER = LoggerFactory.getLogger(EgovArticleController.class);
	
	@Resource(name = "EgovArticleService")
    private EgovArticleService egovArticleService;

    @Resource(name = "EgovBBSMasterService")
    private EgovBBSMasterService egovBBSMasterService;

    @Resource(name = "EgovFileMngService")
    private EgovFileMngService fileMngService;

    @Resource(name = "EgovFileMngUtil")
    private EgovFileMngUtil fileUtil;

    @Resource(name = "propertiesService")
    protected EgovPropertyService propertyService;
    
    @Resource(name="egovMessageSource")
    EgovMessageSource egovMessageSource;
    
    @Resource(name = "EgovArticleCommentService")
    protected EgovArticleCommentService egovArticleCommentService;
    

    @Autowired
    private DefaultBeanValidator beanValidator;

    //protected Logger log = Logger.getLogger(this.getClass());
    
    /**
     * XSS 방지 처리.
     * 
     * @param data
     * @return
     */
    protected String unscript(String data) {
        if (data == null || data.trim().equals("")) {
            return "";
        }
        
        String ret = data;
        
        ret = ret.replaceAll("<(S|s)(C|c)(R|r)(I|i)(P|p)(T|t)", "&lt;script");
        ret = ret.replaceAll("</(S|s)(C|c)(R|r)(I|i)(P|p)(T|t)", "&lt;/script");
        
        ret = ret.replaceAll("<(O|o)(B|b)(J|j)(E|e)(C|c)(T|t)", "&lt;object");
        ret = ret.replaceAll("</(O|o)(B|b)(J|j)(E|e)(C|c)(T|t)", "&lt;/object");
        
        ret = ret.replaceAll("<(A|a)(P|p)(P|p)(L|l)(E|e)(T|t)", "&lt;applet");
        ret = ret.replaceAll("</(A|a)(P|p)(P|p)(L|l)(E|e)(T|t)", "&lt;/applet");
        
        ret = ret.replaceAll("<(E|e)(M|m)(B|b)(E|e)(D|d)", "&lt;embed");
        ret = ret.replaceAll("</(E|e)(M|m)(B|b)(E|e)(D|d)", "&lt;embed");
        
        ret = ret.replaceAll("<(F|f)(O|o)(R|r)(M|m)", "&lt;form");
        ret = ret.replaceAll("</(F|f)(O|o)(R|r)(M|m)", "&lt;form");

        return ret;
    }

    /**
     * 게시물에 대한 목록을 조회한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/selectArticleList.do")
    public String selectArticleList(@ModelAttribute("searchVO") BoardVO boardVO, ModelMap model) throws Exception {
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
	
		BoardMasterVO vo = new BoardMasterVO();
		
		vo.setBbsId(boardVO.getBbsId());
		vo.setUniqId(user.getUniqId());
		BoardMasterVO master = egovBBSMasterService.selectBBSMasterInf(vo);
		
		//방명록은 방명록 게시판으로 이동
		if(master.getBbsTyCode().equals("BBST03")){
			return "forward:/cop/bbs/selectGuestArticleList.do";
		}
		
		
		boardVO.setPageUnit(propertyService.getInt("pageUnit"));
		boardVO.setPageSize(propertyService.getInt("pageSize"));
	
		PaginationInfo paginationInfo = new PaginationInfo();
		
		paginationInfo.setCurrentPageNo(boardVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(boardVO.getPageUnit());
		paginationInfo.setPageSize(boardVO.getPageSize());
	
		boardVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		boardVO.setLastIndex(paginationInfo.getLastRecordIndex());
		boardVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());
	
		Map<String, Object> map = egovArticleService.selectArticleList(boardVO);
		int totCnt = Integer.parseInt((String)map.get("resultCnt"));
		
		//공지사항 추출
		List<BoardVO> noticeList = egovArticleService.selectNoticeArticleList(boardVO);
		
		paginationInfo.setTotalRecordCount(totCnt);
	
		//-------------------------------
		// 기본 BBS template 지정 
		//-------------------------------
		if (master.getTmplatCours() == null || master.getTmplatCours().equals("")) {
		    master.setTmplatCours("/css/egovframework/com/cop/tpl/egovBaseTemplate.css");
		}
		////-----------------------------
	
		if(user != null) {
	    	model.addAttribute("sessionUniqId", user.getUniqId());
	    }
		
		model.addAttribute("resultList", map.get("resultList"));
		model.addAttribute("resultCnt", map.get("resultCnt"));
		model.addAttribute("articleVO", boardVO);
		model.addAttribute("boardMasterVO", master);
		model.addAttribute("paginationInfo", paginationInfo);
		model.addAttribute("noticeList", noticeList);
		return "egovframework/com/cop/bbs/EgovArticleList";
    }

    /** 삭제 추가  **/
    @RequestMapping("/delete.do")
    public String delete(BoardVO BoardVo, Model m){
        
    	try {
    		egovArticleService.delete(BoardVo);
    		m.addAttribute("sucess", "sucess");
		} catch (Exception e) {
			// TODO: handle exception
			e.getMessage();
		}
    	return "jsonView";
    }
    
    /**
     * 게시물에 대한 상세 정보를 조회한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/selectArticleDetail.do")
    public String selectArticleDetail(@ModelAttribute("searchVO") BoardVO boardVO, ModelMap model) throws Exception {
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
	
		boardVO.setLastUpdusrId(user.getUniqId());
		BoardVO vo = egovArticleService.selectArticleDetail(boardVO);
	
		model.addAttribute("result", vo);
		model.addAttribute("sessionUniqId", user.getUniqId());
		
		//비밀글은 작성자만 볼수 있음 
		if(!EgovStringUtil.isEmpty(vo.getSecretAt()) && vo.getSecretAt().equals("Y") && !user.getUniqId().equals(vo.getFrstRegisterId()))
			return"forward:/cop/bbs/selectArticleList.do";
		
		//----------------------------
		// template 처리 (기본 BBS template 지정  포함)
		//----------------------------
		BoardMasterVO master = new BoardMasterVO();
		
		master.setBbsId(boardVO.getBbsId());
		master.setUniqId(user.getUniqId());
		
		BoardMasterVO masterVo = egovBBSMasterService.selectBBSMasterInf(master);
	
		if (masterVo.getTmplatCours() == null || masterVo.getTmplatCours().equals("")) {
		    masterVo.setTmplatCours("/css/egovframework/com/cop/tpl/egovBaseTemplate.css");
		}
	
		model.addAttribute("boardMasterVO", masterVo);
	
		return "egovframework/com/cop/bbs/EgovArticleDetail";
    }

    /**
     * 게시물 등록을 위한 등록페이지로 이동한다.
     * 
     * @param boardVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/insertArticleView.do")
    public String insertArticleView(@ModelAttribute("searchVO") BoardVO boardVO, ModelMap model) throws Exception {
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
	
		BoardMasterVO bdMstr = new BoardMasterVO();
		BoardVO board = new BoardVO();
		if (isAuthenticated) {
	
		    BoardMasterVO vo = new BoardMasterVO();
		    vo.setBbsId(boardVO.getBbsId());
		    vo.setUniqId(user.getUniqId());
	
		    bdMstr = egovBBSMasterService.selectBBSMasterInf(vo);
		}
	
		//----------------------------
		// 기본 BBS template 지정 
		//----------------------------
		if (bdMstr.getTmplatCours() == null || bdMstr.getTmplatCours().equals("")) {
		    bdMstr.setTmplatCours("/css/egovframework/com/cop/tpl/egovBaseTemplate.css");
		}
	
		model.addAttribute("articleVO", boardVO);
		model.addAttribute("boardMasterVO", bdMstr);
		////-----------------------------
	
		return "egovframework/com/cop/bbs/EgovArticleRegist";
    }

    /**
     * 게시물을 등록한다.
     * 
     * @param boardVO
     * @param board
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/insertArticle.do")
    public String insertArticle(final MultipartHttpServletRequest multiRequest, @ModelAttribute("searchVO") BoardVO boardVO,
	    @ModelAttribute("bdMstr") BoardMaster bdMstr, @ModelAttribute("board") BoardVO board, BindingResult bindingResult, 
	    ModelMap model) throws Exception {

		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
	
		beanValidator.validate(board, bindingResult);
		if (bindingResult.hasErrors()) {
	
		    BoardMasterVO master = new BoardMasterVO();
		    
		    master.setBbsId(boardVO.getBbsId());
		    master.setUniqId(user.getUniqId());
	
		    master = egovBBSMasterService.selectBBSMasterInf(master);
		    
	
		    //----------------------------
		    // 기본 BBS template 지정 
		    //----------------------------
		    if (master.getTmplatCours() == null || master.getTmplatCours().equals("")) {
			master.setTmplatCours("css/egovframework/com/cop/tpl/egovBaseTemplate.css");
		    }
	
		    model.addAttribute("boardMasterVO", master);
		    ////-----------------------------
	
		    return "egovframework/com/cop/bbs/EgovArticleRegist";
		}
	
		if (isAuthenticated) {
		    List<FileVO> result = null;
		    String atchFileId = "";
		    
		    final Map<String, MultipartFile> files = multiRequest.getFileMap();
		    if (!files.isEmpty()) {
			result = fileUtil.parseFileInf(files, "BBS_", 0, "", "");
			atchFileId = fileMngService.insertFileInfs(result);
		    }
		    board.setAtchFileId(atchFileId);
		    board.setFrstRegisterId(user.getUniqId());
		    board.setBbsId(boardVO.getBbsId());
		    board.setBlogId(boardVO.getBlogId());
		    
		    
		    //익명등록 처리 
		    if(board.getAnonymousAt() != null && board.getAnonymousAt().equals("Y")){
		    	board.setNtcrId("anonymous"); //게시물 통계 집계를 위해 등록자 ID 저장
		    	board.setNtcrNm("익명"); //게시물 통계 집계를 위해 등록자 Name 저장
		    	board.setFrstRegisterId("anonymous");
		    	
		    } else {
		    	board.setNtcrId(user.getId()); //게시물 통계 집계를 위해 등록자 ID 저장
		    	board.setNtcrNm(user.getName()); //게시물 통계 집계를 위해 등록자 Name 저장
		    	
		    }
		    
		    board.setNttCn(unscript(board.getNttCn()));	// XSS 방지
		    egovArticleService.insertArticle(board);
		}
		//status.setComplete();
		if(boardVO.getBlogAt().equals("Y")){
			return "forward:/cop/bbs/selectArticleBlogList.do";
		}else{
			return "forward:/cop/bbs/selectArticleList.do";
		}
		
    }

    /**
     * 게시물에 대한 답변 등록을 위한 등록페이지로 이동한다.
     * 
     * @param boardVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/replyArticleView.do")
    public String addReplyBoardArticle(@ModelAttribute("searchVO") BoardVO boardVO, ModelMap model) throws Exception {
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
	
		BoardMasterVO master = new BoardMasterVO();
		BoardVO articleVO = new BoardVO();
		master.setBbsId(boardVO.getBbsId());
		master.setUniqId(user.getUniqId());
	
		master = egovBBSMasterService.selectBBSMasterInf(master);
		boardVO = egovArticleService.selectArticleDetail(boardVO);
		
		//----------------------------
		// 기본 BBS template 지정 
		//----------------------------
		if (master.getTmplatCours() == null || master.getTmplatCours().equals("")) {
		    master.setTmplatCours("/css/egovframework/com/cop/tpl/egovBaseTemplate.css");
		}
	
		model.addAttribute("boardMasterVO", master);
		model.addAttribute("result", boardVO);
	
		model.addAttribute("articleVO", articleVO);
		
		if(boardVO.getBlogAt().equals("chkBlog")){
			return "egovframework/com/cop/bbs/EgovArticleBlogReply";
		}else{
			return "egovframework/com/cop/bbs/EgovArticleReply";
		}
    }

    /**
     * 게시물에 대한 답변을 등록한다.
     * 
     * @param boardVO
     * @param board
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/replyArticle.do")
    public String replyBoardArticle(final MultipartHttpServletRequest multiRequest, @ModelAttribute("searchVO") BoardVO boardVO,
	    @ModelAttribute("bdMstr") BoardMaster bdMstr, @ModelAttribute("board") BoardVO board, BindingResult bindingResult, ModelMap model
	    ) throws Exception {

		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
		
		beanValidator.validate(board, bindingResult);
		if (bindingResult.hasErrors()) {
		    BoardMasterVO master = new BoardMasterVO();
		    
		    master.setBbsId(boardVO.getBbsId());
		    master.setUniqId(user.getUniqId());
	
		    master = egovBBSMasterService.selectBBSMasterInf(master);
		    
	
		    //----------------------------
		    // 기본 BBS template 지정 
		    //----------------------------
		    if (master.getTmplatCours() == null || master.getTmplatCours().equals("")) {
			master.setTmplatCours("/css/egovframework/com/cop/tpl/egovBaseTemplate.css");
		    }
	
		    model.addAttribute("articleVO", boardVO);
		    model.addAttribute("boardMasterVO", master);
		    ////-----------------------------
	
		    return "egovframework/com/cop/bbs/EgovArticleReply";
		}
	
		if (isAuthenticated) {
		    final Map<String, MultipartFile> files = multiRequest.getFileMap();
		    String atchFileId = "";
	
		    if (!files.isEmpty()) {
			List<FileVO> result = fileUtil.parseFileInf(files, "BBS_", 0, "", "");
			atchFileId = fileMngService.insertFileInfs(result);
		    }
	
		    board.setAtchFileId(atchFileId);
		    board.setReplyAt("Y");
		    board.setFrstRegisterId(user.getUniqId());
		    board.setBbsId(board.getBbsId());
		    board.setParnts(Long.toString(boardVO.getNttId()));
		    board.setSortOrdr(boardVO.getSortOrdr());
		    board.setReplyLc(Integer.toString(Integer.parseInt(boardVO.getReplyLc()) + 1));
		    
		  //익명등록 처리 
		    if(board.getAnonymousAt() != null && board.getAnonymousAt().equals("Y")){
		    	board.setNtcrId("anonymous"); //게시물 통계 집계를 위해 등록자 ID 저장
		    	board.setNtcrNm("익명"); //게시물 통계 집계를 위해 등록자 Name 저장
		    	board.setFrstRegisterId("anonymous");
		    	
		    } else {
		    	board.setNtcrId(user.getId()); //게시물 통계 집계를 위해 등록자 ID 저장
		    	board.setNtcrNm(user.getName()); //게시물 통계 집계를 위해 등록자 Name 저장
		    	
		    }
		    board.setNttCn(unscript(board.getNttCn()));	// XSS 방지
		    
		    egovArticleService.insertArticle(board);
		}
		
		return "forward:/cop/bbs/selectArticleList.do";
    }

    /**
     * 게시물 수정을 위한 수정페이지로 이동한다.
     * 
     * @param boardVO
     * @param vo
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/updateArticleView.do")
    public String updateArticleView(@ModelAttribute("searchVO") BoardVO boardVO, @ModelAttribute("board") BoardVO vo, ModelMap model)
	    throws Exception {

		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
	
		boardVO.setFrstRegisterId(user.getUniqId());
		
		BoardMasterVO bmvo = new BoardMasterVO();
		BoardVO bdvo = new BoardVO();
		
		vo.setBbsId(boardVO.getBbsId());
		
		bmvo.setBbsId(boardVO.getBbsId());
		bmvo.setUniqId(user.getUniqId());
	
		if (isAuthenticated) {
		    bmvo = egovBBSMasterService.selectBBSMasterInf(bmvo);
		    bdvo = egovArticleService.selectArticleDetail(boardVO);
		}
	
		//----------------------------
		// 기본 BBS template 지정 
		//----------------------------
		if (bmvo.getTmplatCours() == null || bmvo.getTmplatCours().equals("")) {
		    bmvo.setTmplatCours("/css/egovframework/com/cop/tpl/egovBaseTemplate.css");
		}
	
		//익명 등록글인 경우 수정 불가 
		if(bdvo.getNtcrId().equals("anonymous")){
			model.addAttribute("result", bdvo);
			model.addAttribute("boardMasterVO", bmvo);
			return "egovframework/com/cop/bbs/EgovArticleDetail";
		}
		
		model.addAttribute("articleVO", bdvo);
		model.addAttribute("boardMasterVO", bmvo);
		
		if(boardVO.getBlogAt().equals("chkBlog")){
			return "egovframework/com/cop/bbs/EgovArticleBlogUpdt";
		}else{
			return "egovframework/com/cop/bbs/EgovArticleUpdt";
		}
		
    }

    /**
     * 게시물에 대한 내용을 수정한다.
     * 
     * @param boardVO
     * @param board
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/updateArticle.do")
    public String updateBoardArticle(final MultipartHttpServletRequest multiRequest, @ModelAttribute("searchVO") BoardVO boardVO,
	    @ModelAttribute("bdMstr") BoardMaster bdMstr, @ModelAttribute("board") Board board, BindingResult bindingResult, ModelMap model) throws Exception {

		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
		
		//--------------------------------------------------------------------------------------------
    	// @ XSS 대응 권한체크 체크  START
    	// param1 : 사용자고유ID(uniqId,esntlId)
    	//--------------------------------------------------------
    	LOGGER.debug("@ XSS 권한체크 START ----------------------------------------------");
    	//step1 DB에서 해당 게시물의 uniqId 조회
    	BoardVO vo = egovArticleService.selectArticleDetail(boardVO);
    	
    	//step2 EgovXssChecker 공통모듈을 이용한 권한체크
    	EgovXssChecker.checkerUserXss(multiRequest, vo.getFrstRegisterId()); 
      	LOGGER.debug("@ XSS 권한체크 END ------------------------------------------------");
    	//--------------------------------------------------------
    	// @ XSS 대응 권한체크 체크 END
    	//--------------------------------------------------------------------------------------------
	
		String atchFileId = boardVO.getAtchFileId();
	
		beanValidator.validate(board, bindingResult);
		if (bindingResult.hasErrors()) {
	
		    boardVO.setFrstRegisterId(user.getUniqId());
		    
		    BoardMasterVO bmvo = new BoardMasterVO();
		    BoardVO bdvo = new BoardVO();
		    
		    bmvo.setBbsId(boardVO.getBbsId());
		    bmvo.setUniqId(user.getUniqId());
	
		    bmvo = egovBBSMasterService.selectBBSMasterInf(bmvo);
		    bdvo = egovArticleService.selectArticleDetail(boardVO);
	
		    model.addAttribute("articleVO", bdvo);
		    model.addAttribute("boardMasterVO", bmvo);
	
		    return "egovframework/com/cop/bbs/EgovArticleUpdt";
		}
		
		if (isAuthenticated) {
		    final Map<String, MultipartFile> files = multiRequest.getFileMap();
		    if (!files.isEmpty()) {
				if ("".equals(atchFileId)) {
				    List<FileVO> result = fileUtil.parseFileInf(files, "BBS_", 0, atchFileId, "");
				    atchFileId = fileMngService.insertFileInfs(result);
				    board.setAtchFileId(atchFileId);
				} else {
				    FileVO fvo = new FileVO();
				    fvo.setAtchFileId(atchFileId);
				    int cnt = fileMngService.getMaxFileSN(fvo);
				    List<FileVO> _result = fileUtil.parseFileInf(files, "BBS_", cnt, atchFileId, "");
				    fileMngService.updateFileInfs(_result);
				}
		    }
	
		    board.setLastUpdusrId(user.getUniqId());
		    
		    board.setNtcrNm("");	// dummy 오류 수정 (익명이 아닌 경우 validator 처리를 위해 dummy로 지정됨)
		    board.setPassword("");	// dummy 오류 수정 (익명이 아닌 경우 validator 처리를 위해 dummy로 지정됨)
		    
		    board.setNttCn(unscript(board.getNttCn()));	// XSS 방지
		    
		    egovArticleService.updateArticle(board);
		}
		
		return "forward:/cop/bbs/selectArticleList.do";
    }

    /**
     * 게시물에 대한 내용을 삭제한다.
     * 
     * @param boardVO
     * @param board
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/deleteArticle.do")
    public String deleteBoardArticle(HttpServletRequest request, @ModelAttribute("searchVO") BoardVO boardVO, @ModelAttribute("board") Board board,
	    @ModelAttribute("bdMstr") BoardMaster bdMstr, ModelMap model) throws Exception {
	
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
	
		//--------------------------------------------------------------------------------------------
    	// @ XSS 대응 권한체크 체크  START
    	// param1 : 사용자고유ID(uniqId,esntlId)
    	//--------------------------------------------------------
    	LOGGER.debug("@ XSS 권한체크 START ----------------------------------------------");
    	//step1 DB에서 해당 게시물의 uniqId 조회
    	BoardVO vo = egovArticleService.selectArticleDetail(boardVO);
    	
    	//step2 EgovXssChecker 공통모듈을 이용한 권한체크
    	EgovXssChecker.checkerUserXss(request, vo.getFrstRegisterId()); 
      	LOGGER.debug("@ XSS 권한체크 END ------------------------------------------------");
    	//--------------------------------------------------------
    	// @ XSS 대응 권한체크 체크 END
    	//--------------------------------------------------------------------------------------------
		
		BoardVO bdvo = egovArticleService.selectArticleDetail(boardVO);
		//익명 등록글인 경우 수정 불가 
		if(bdvo.getNtcrId().equals("anonymous")){
			model.addAttribute("result", bdvo);
			model.addAttribute("boardMasterVO", bdMstr);
			return "egovframework/com/cop/bbs/EgovArticleDetail";
		}
		
		if (isAuthenticated) {
		    board.setLastUpdusrId(user.getUniqId());
		    
		    egovArticleService.deleteArticle(board);
		}
		
		if(boardVO.getBlogAt().equals("chkBlog")){
			return "forward:/cop/bbs/selectArticleBlogList.do";
		}else{
			return "forward:/cop/bbs/selectArticleList.do";
		}
    }
    
    /**
     * 방명록에 대한 목록을 조회한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/selectGuestArticleList.do")
    public String selectGuestArticleList(@ModelAttribute("searchVO") BoardVO boardVO, ModelMap model) throws Exception {

		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		@SuppressWarnings("unused")
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
		
		// 수정 및 삭제 기능 제어를 위한 처리
		model.addAttribute("sessionUniqId", user.getUniqId());
		
		BoardVO vo = new BoardVO();
	
		vo.setBbsId(boardVO.getBbsId());
		vo.setBbsNm(boardVO.getBbsNm());
		vo.setNtcrNm(user.getName());
		vo.setNtcrId(user.getUniqId());
	
		BoardMasterVO masterVo = new BoardMasterVO();
		
		masterVo.setBbsId(vo.getBbsId());
		masterVo.setUniqId(user.getUniqId());
		
		BoardMasterVO mstrVO = egovBBSMasterService.selectBBSMasterInf(masterVo);
	
		vo.setPageIndex(boardVO.getPageIndex());
		vo.setPageUnit(propertyService.getInt("pageUnit"));
		vo.setPageSize(propertyService.getInt("pageSize"));
	
		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(vo.getPageIndex());
		paginationInfo.setRecordCountPerPage(vo.getPageUnit());
		paginationInfo.setPageSize(vo.getPageSize());
	
		vo.setFirstIndex(paginationInfo.getFirstRecordIndex());
		vo.setLastIndex(paginationInfo.getLastRecordIndex());
		vo.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());
	
		Map<String, Object> map = egovArticleService.selectGuestArticleList(vo);
		int totCnt = Integer.parseInt((String)map.get("resultCnt"));
		
		paginationInfo.setTotalRecordCount(totCnt);
	
		model.addAttribute("user", user);
		model.addAttribute("resultList", map.get("resultList"));
		model.addAttribute("resultCnt", map.get("resultCnt"));
		model.addAttribute("boardMasterVO", mstrVO);
		model.addAttribute("articleVO", vo);
		model.addAttribute("paginationInfo", paginationInfo);
	
		return "egovframework/com/cop/bbs/EgovGuestArticleList";
    }
    
	
    /**
     * 방명록에 대한 내용을 등록한다.
     * 
     * @param boardVO
     * @param board
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/insertGuestArticle.do")
    public String insertGuestList(@ModelAttribute("searchVO") BoardVO boardVO, @ModelAttribute("Board") Board board, BindingResult bindingResult,
	    ModelMap model) throws Exception {

		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
	
		beanValidator.validate(board, bindingResult);
		if (bindingResult.hasErrors()) {
	
		    BoardVO vo = new BoardVO();
	
		    vo.setBbsId(boardVO.getBbsId());
		    vo.setBbsNm(boardVO.getBbsNm());
		    vo.setNtcrNm(user.getName());
		    vo.setNtcrId(user.getUniqId());
	
		    BoardMasterVO masterVo = new BoardMasterVO();
		    
		    masterVo.setBbsId(vo.getBbsId());
		    masterVo.setUniqId(user.getUniqId());
		    
		    BoardMasterVO mstrVO = egovBBSMasterService.selectBBSMasterInf(masterVo);
	
		    vo.setPageUnit(propertyService.getInt("pageUnit"));
		    vo.setPageSize(propertyService.getInt("pageSize"));
	
		    PaginationInfo paginationInfo = new PaginationInfo();
		    paginationInfo.setCurrentPageNo(vo.getPageIndex());
		    paginationInfo.setRecordCountPerPage(vo.getPageUnit());
		    paginationInfo.setPageSize(vo.getPageSize());
	
		    vo.setFirstIndex(paginationInfo.getFirstRecordIndex());
		    vo.setLastIndex(paginationInfo.getLastRecordIndex());
		    vo.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());
	
		    Map<String, Object> map = egovArticleService.selectGuestArticleList(vo);
		    int totCnt = Integer.parseInt((String)map.get("resultCnt"));
		    
		    paginationInfo.setTotalRecordCount(totCnt);
	
		    model.addAttribute("resultList", map.get("resultList"));
		    model.addAttribute("resultCnt", map.get("resultCnt"));
		    model.addAttribute("boardMasterVO", mstrVO);
		    model.addAttribute("articleVO", vo);	    
		    model.addAttribute("paginationInfo", paginationInfo);
	
		    return "egovframework/com/cop/bbs/EgovGuestArticleList";
	
		}
	
		if (isAuthenticated) {
		    board.setFrstRegisterId(user.getUniqId());
		    
		    egovArticleService.insertArticle(board);
	
		    boardVO.setNttCn("");
		    boardVO.setPassword("");
		    boardVO.setNtcrId("");
		    boardVO.setNttId(0);
		}
	
		return "forward:/cop/bbs/selectGuestArticleList.do";
    }
    
    /**
     * 방명록에 대한 내용을 삭제한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/deleteGuestArticle.do")
    public String deleteGuestList(@ModelAttribute("searchVO") BoardVO boardVO, @ModelAttribute("articleVO") Board board, ModelMap model) throws Exception {
		@SuppressWarnings("unused")
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
		
		if (isAuthenticated) {
		    egovArticleService.deleteArticle(boardVO);
		}
		
		return "forward:/cop/bbs/selectGuestArticleList.do";
    }
    
    /**
     * 방명록 수정을 위한 특정 내용을 조회한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/updateGuestArticleView.do")
    public String updateGuestArticleView(@ModelAttribute("searchVO") BoardVO boardVO, @ModelAttribute("boardMasterVO") BoardMasterVO brdMstrVO,
	    ModelMap model) throws Exception {

		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		@SuppressWarnings("unused")
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
	
		// 수정 및 삭제 기능 제어를 위한 처리
		model.addAttribute("sessionUniqId", user.getUniqId());
		
		BoardVO vo = egovArticleService.selectArticleDetail(boardVO);
	
		boardVO.setBbsId(boardVO.getBbsId());
		boardVO.setBbsNm(boardVO.getBbsNm());
		boardVO.setNtcrNm(user.getName());
	
		boardVO.setPageUnit(propertyService.getInt("pageUnit"));
		boardVO.setPageSize(propertyService.getInt("pageSize"));
	
		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(boardVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(boardVO.getPageUnit());
		paginationInfo.setPageSize(boardVO.getPageSize());
	
		boardVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		boardVO.setLastIndex(paginationInfo.getLastRecordIndex());
		boardVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());
	
		Map<String, Object> map = egovArticleService.selectGuestArticleList(boardVO);
		int totCnt = Integer.parseInt((String)map.get("resultCnt"));
		
		paginationInfo.setTotalRecordCount(totCnt);
	
		model.addAttribute("resultList", map.get("resultList"));
		model.addAttribute("resultCnt", map.get("resultCnt"));
		model.addAttribute("articleVO", vo);
		model.addAttribute("paginationInfo", paginationInfo);
	
		return "egovframework/com/cop/bbs/EgovGuestArticleList";
    }
    
    /**
     * 방명록을 수정하고 게시판 메인페이지를 조회한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/updateGuestArticle.do")
    public String updateGuestArticle(@ModelAttribute("searchVO") BoardVO boardVO, @ModelAttribute Board board, BindingResult bindingResult,
	    ModelMap model) throws Exception {

		//BBST02, BBST04
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		Boolean isAuthenticated = EgovUserDetailsHelper.isAuthenticated();
	
		beanValidator.validate(board, bindingResult);
		if (bindingResult.hasErrors()) {
	
		    BoardVO vo = new BoardVO();
	
		    vo.setBbsId(boardVO.getBbsId());
		    vo.setBbsNm(boardVO.getBbsNm());
		    vo.setNtcrNm(user.getName());
		    vo.setNtcrId(user.getUniqId());
	
		    BoardMasterVO masterVo = new BoardMasterVO();
		    
		    masterVo.setBbsId(vo.getBbsId());
		    masterVo.setUniqId(user.getUniqId());
		    
		    BoardMasterVO mstrVO = egovBBSMasterService.selectBBSMasterInf(masterVo);
	
		    vo.setPageUnit(propertyService.getInt("pageUnit"));
		    vo.setPageSize(propertyService.getInt("pageSize"));
	
		    PaginationInfo paginationInfo = new PaginationInfo();
		    paginationInfo.setCurrentPageNo(vo.getPageIndex());
		    paginationInfo.setRecordCountPerPage(vo.getPageUnit());
		    paginationInfo.setPageSize(vo.getPageSize());
	
		    vo.setFirstIndex(paginationInfo.getFirstRecordIndex());
		    vo.setLastIndex(paginationInfo.getLastRecordIndex());
		    vo.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());
	
		    Map<String, Object> map = egovArticleService.selectGuestArticleList(vo);
		    int totCnt = Integer.parseInt((String)map.get("resultCnt"));
	
		    paginationInfo.setTotalRecordCount(totCnt);
		    
		    model.addAttribute("resultList", map.get("resultList"));
		    model.addAttribute("resultCnt", map.get("resultCnt"));
		    model.addAttribute("boardMasterVO", mstrVO);
		    model.addAttribute("articleVO", vo);
		    model.addAttribute("paginationInfo", paginationInfo);
	
		    return "egovframework/com/cop/bbs/EgovGuestArticleList";
		}
	
		if (isAuthenticated) {
		    egovArticleService.updateArticle(board);
		    boardVO.setNttCn("");
		    boardVO.setPassword("");
		    boardVO.setNtcrId("");
		    boardVO.setNttId(0);
		}
	
		return "forward:/cop/bbs/selectGuestArticleList.do";
    }
    
    /*********************
     * 블로그관련
     * ********************/
    
    /**
     * 블로그 게시판에 대한 목록을 조회한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/selectArticleBlogList.do")
    public String selectArticleBlogList(@ModelAttribute("searchVO") BoardVO boardVO, ModelMap model) throws Exception {
    	
    	LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		
		BlogVO blogVo = new BlogVO();
		blogVo.setFrstRegisterId(user.getUniqId());
		blogVo.setBbsId(boardVO.getBbsId());
		blogVo.setBlogId(boardVO.getBlogId());
		BlogVO master = egovBBSMasterService.selectBlogDetail(blogVo);
		
		boardVO.setFrstRegisterId(user.getUniqId());

		//블로그 카테고리관리 권한(로그인 한 사용자만 가능)
		int loginUserCnt =  egovArticleService.selectLoginUser(boardVO);
		
		//블로그 게시판 제목 추출
		List<BoardVO> blogNameList = egovArticleService.selectBlogNmList(boardVO);

		if(user != null) {
	    	model.addAttribute("sessionUniqId", user.getUniqId());
	    }
		
		model.addAttribute("articleVO", boardVO);
		model.addAttribute("boardMasterVO", master);
		model.addAttribute("blogNameList", blogNameList);
		model.addAttribute("loginUserCnt", loginUserCnt);
		
		return "egovframework/com/cop/bbs/EgovArticleBlogList";
    }
    
    /**
     * 블로그 게시물에 대한 상세 타이틀을 조회한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/selectArticleBlogDetail.do")
    public ModelAndView selectArticleBlogDetail(@ModelAttribute("searchVO") BoardVO boardVO, ModelMap model) throws Exception {
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		BoardVO vo = new BoardVO();
		
		boardVO.setLastUpdusrId(user.getUniqId());
		
		boardVO.setPageUnit(propertyService.getInt("pageUnit"));
		boardVO.setPageSize(propertyService.getInt("pageSize"));
		
		PaginationInfo paginationInfo = new PaginationInfo();
		
		paginationInfo.setCurrentPageNo(boardVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(boardVO.getPageUnit());
		paginationInfo.setPageSize(boardVO.getPageSize());
		
		boardVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		boardVO.setLastIndex(paginationInfo.getLastRecordIndex());
		boardVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());
		
		List<BoardVO> blogSubJectList = egovArticleService.selectArticleDetailDefault(boardVO);
		vo = egovArticleService.selectArticleCnOne(boardVO);
		
		int totCnt = egovArticleService.selectArticleDetailDefaultCnt(boardVO);
		paginationInfo.setTotalRecordCount(totCnt);
		
		ModelAndView mav = new ModelAndView("jsonView");
		mav.addObject("blogSubJectList", blogSubJectList);
		mav.addObject("paginationInfo", paginationInfo);
		
		if(vo.getNttCn() != null){
			mav.addObject("blogCnOne", vo);
		}
		
		//비밀글은 작성자만 볼수 있음 
		if(!EgovStringUtil.isEmpty(vo.getSecretAt()) && vo.getSecretAt().equals("Y") && !user.getUniqId().equals(vo.getFrstRegisterId()))
		mav.setViewName("forward:/cop/bbs/selectArticleList.do");
		return mav;
    }
    
    /**
     * 블로그 게시물에 대한 상세 내용을 조회한다.
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/selectArticleBlogDetailCn.do")
    public ModelAndView selectArticleBlogDetailCn(@ModelAttribute("searchVO") BoardVO boardVO, @ModelAttribute("commentVO") CommentVO commentVO, ModelMap model) throws Exception {
		LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
		
		boardVO.setLastUpdusrId(user.getUniqId());
		
		BoardVO vo = egovArticleService.selectArticleDetail(boardVO);
		
		//----------------------------
		// 댓글 처리
		//----------------------------
		CommentVO articleCommentVO = new CommentVO();
		commentVO.setWrterNm(user.getName());
		
		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(commentVO.getSubPageIndex());
		paginationInfo.setRecordCountPerPage(commentVO.getSubPageUnit());
		paginationInfo.setPageSize(commentVO.getSubPageSize());
	
		commentVO.setSubFirstIndex(paginationInfo.getFirstRecordIndex());
		commentVO.setSubLastIndex(paginationInfo.getLastRecordIndex());
		commentVO.setSubRecordCountPerPage(paginationInfo.getRecordCountPerPage());
	
		Map<String, Object> map = egovArticleCommentService.selectArticleCommentList(commentVO);
		int totCnt = Integer.parseInt((String)map.get("resultCnt"));
		
		paginationInfo.setTotalRecordCount(totCnt);
		
	    //댓글 처리 END
		//----------------------------
		
		List<BoardVO> blogCnList = egovArticleService.selectArticleDetailCn(boardVO);
		ModelAndView mav = new ModelAndView("jsonView");
		
		// 수정 처리된 후 댓글 등록 화면으로 처리되기 위한 구현
		if (commentVO.isModified()) {
		    commentVO.setCommentNo("");
		    commentVO.setCommentCn("");
		}
		
		// 수정을 위한 처리
		if (!commentVO.getCommentNo().equals("")) {
			mav.setViewName ("forward:/cop/cmt/updateArticleCommentView.do");
		}
		
		mav.addObject("blogCnList", blogCnList);
		mav.addObject("resultUnder", vo);
		mav.addObject("paginationInfo", paginationInfo);
		mav.addObject("resultList", map.get("resultList"));
		mav.addObject("resultCnt", map.get("resultCnt"));
		mav.addObject("articleCommentVO", articleCommentVO);	// validator 용도
		
		commentVO.setCommentCn("");	// 등록 후 댓글 내용 처리
		
		//비밀글은 작성자만 볼수 있음 
		if(!EgovStringUtil.isEmpty(vo.getSecretAt()) && vo.getSecretAt().equals("Y") && !user.getUniqId().equals(vo.getFrstRegisterId()))
		mav.setViewName("forward:/cop/bbs/selectArticleList.do");
		return mav;
		
    }
    
    /**
     * 개인블로그 관리 
     * 
     * @param boardVO
     * @param sessionVO
     * @param model
     * @return
     * @throws Exception
     */
    @RequestMapping("/cop/bbs/selectBlogListManager.do")
    public String selectBlogMasterList(@ModelAttribute("searchVO") BoardVO boardVO, ModelMap model) throws Exception {
    	
    	LoginVO user = (LoginVO)EgovUserDetailsHelper.getAuthenticatedUser();
    	
    	
    	boardVO.setPageUnit(propertyService.getInt("pageUnit"));
    	boardVO.setPageSize(propertyService.getInt("pageSize"));
	
		PaginationInfo paginationInfo = new PaginationInfo();
		
		paginationInfo.setCurrentPageNo(boardVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(boardVO.getPageUnit());
		paginationInfo.setPageSize(boardVO.getPageSize());
	
		boardVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		boardVO.setLastIndex(paginationInfo.getLastRecordIndex());
		boardVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());
		boardVO.setFrstRegisterId(user.getUniqId());
		
		Map<String, Object> map = egovArticleService.selectBlogListManager(boardVO);
		int totCnt = Integer.parseInt((String)map.get("resultCnt"));
		
		paginationInfo.setTotalRecordCount(totCnt);
	
		model.addAttribute("resultList", map.get("resultList"));
		model.addAttribute("resultCnt", map.get("resultCnt"));	
		model.addAttribute("paginationInfo", paginationInfo);
    	
    	return "egovframework/com/cop/bbs/EgovBlogListManager";
    }
    
}
