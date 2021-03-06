package com.galaxy.project.service.impl;

import com.galaxy.project.core.AbstractService;
import com.galaxy.project.core.Result;
import com.galaxy.project.core.ResultCode;
import com.galaxy.project.core.ResultGenerator;
import com.galaxy.project.dao.CmsBlogImagesMapper;
import com.galaxy.project.dao.CmsBlogMapper;
import com.galaxy.project.dao.CmsMomentCommentMapper;
import com.galaxy.project.model.Blog;
import com.galaxy.project.model.BlogImages;
import com.galaxy.project.model.BlogTag;
import com.galaxy.project.service.CmsBlogImagesService;
import com.galaxy.project.service.CmsBlogService;
import com.galaxy.project.service.CmsBlogTagService;
import com.galaxy.project.utils.DigitUtil;
import com.galaxy.project.vo.HomeListVo;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
* Created by CodeGenerator on 2020/12/23.
*/
@Service
@Transactional
public class CmsBlogServiceImpl extends AbstractService<Blog> implements CmsBlogService {

    @Resource
    private CmsBlogMapper cmsBlogMapper;

    @Resource
    private CmsBlogImagesMapper cmsBlogImagesMapper;

    @Resource
    private CmsMomentCommentMapper cmsMomentCommentMapper;

    @Resource
    private CmsBlogImagesService cmsBlogImagesService;

   /* @Resource
    private RemoteUploadService remoteUploadService;*/

    @Resource
    private CmsBlogTagService cmsBlogTagService;

    @Override
    public Result add(Blog blog) {
        blog.setId(DigitUtil.generatorLongId());
        blog.setCreatedAt(new Date());
        blog.setIsDelete(false);
        save(blog);

        if (blog.getBlogImagesList().size() > 0){
            for (BlogImages d:blog.getBlogImagesList()) {
                d.setBlogId(blog.getId());
                d.setCreatedAt(new Date());
                d.setIsDelete(false);
            }
            cmsBlogImagesService.saveList(blog.getBlogImagesList());
        }

        List<BlogTag> blogTagList = new ArrayList<BlogTag>();
        BlogTag blogTag;
        //??????????????????
        if (null != blog.getTagName()){
            String[] array = blog.getTagName().split(",");
            for (int i = 0;i < array.length; i++){
                //
                List<String> stringList = cmsBlogTagService.selectAllTag();
                //??????????????????????????????????????????
                if (!stringList.contains(array[i])){
                    blogTag = new BlogTag();
                    blogTag.setIsDelete(false);
                    blogTag.setCreatedAt(new Date());
                    blogTag.setTagName(array[i]);
                    blogTagList.add(blogTag);
                }
            }
        }
        //?????????????????????????????????????????????
        if (blogTagList.size() > 0){
            cmsBlogTagService.save(blogTagList);
        }

        Result result = ResultGenerator.genSuccessResult();
        result.setData(blog);
        return result;
    }

    @Override
    public Result detail(Long id) {

        Blog blog = cmsBlogMapper.detail(id);
        if (null == blog){
            return ResultGenerator.genFailResult(ResultCode.BLOG_DETAIL_ERROR,"??????????????????????????????");
        }

        cmsBlogMapper.updateBlogBrowseNum(id);

        blog.setBlogImagesList(cmsBlogImagesMapper.selectBlogImagesByBlogId(id));
        blog.setMomentCommentList(cmsMomentCommentMapper.selectMomentCommentByBlogId(id));
        return ResultGenerator.genSuccessResult(blog);
    }

    @Override
    public Result list(Integer page, Integer size, Blog blog) {
        PageHelper.startPage(page, size);
        blog.setIsDelete(false);
        List<Blog> list = cmsBlogMapper.list(blog);
        for (Blog d:list) {
            d.setBlogImagesList(cmsBlogImagesMapper.selectBlogImagesByBlogId(d.getId()));
            d.setMomentCommentList(cmsMomentCommentMapper.selectMomentCommentByBlogId(d.getId()));
            d.setCommentNum(d.getMomentCommentList().size());
        }
        PageInfo pageInfo = new PageInfo(list);
        return ResultGenerator.genSuccessResult(pageInfo);
    }

    @Override
    public Result findByModalOrderByTime(Integer page, Integer size, Blog blog) {
        PageHelper.startPage(page, size);
        blog.setIsDelete(false);
        List<Blog> list = cmsBlogMapper.findByModalOrderByTime(blog);
        for (Blog d:list) {
            d.setBlogImagesList(cmsBlogImagesMapper.selectBlogImagesByBlogId(d.getId()));
            d.setMomentCommentList(cmsMomentCommentMapper.selectMomentCommentByBlogId(d.getId()));
            d.setCommentNum(d.getMomentCommentList().size());
        }
        PageInfo pageInfo = new PageInfo(list);
        return ResultGenerator.genSuccessResult(pageInfo);
    }

    @Override
    public Result homeFindByTitle(Integer page, Integer size, String title) {
        PageHelper.startPage(page, size);
        Blog blog = new Blog();
        blog.setTitle(title);
        blog.setIsDelete(false);
        List<Blog> list = cmsBlogMapper.list(blog);
        for (Blog d:list) {
            d.setBlogImagesList(cmsBlogImagesMapper.selectBlogImagesByBlogId(d.getId()));
            d.setMomentCommentList(cmsMomentCommentMapper.selectMomentCommentByBlogId(d.getId()));
        }
        //??????????????????
        PageInfo blogPageInfo = new PageInfo(list);
        HomeListVo homeListVo = new HomeListVo();
        homeListVo.setBlogPageInfo(blogPageInfo);

        //??????????????????
        //Result imagesResult = remoteUploadService.imagesDetailData(page,size,title);
        //homeListVo.setImagesPageInfo(imagesResult.getData());

        //??????????????????
        //Result videoResult = remoteUploadService.imagesDetailData(page,size,title);
        //homeListVo.setVideoPageInfo(videoResult.getData());

        return ResultGenerator.genSuccessResult(homeListVo);
    }

    @Override
    public Result updateBlog(Blog blog) {
        Integer rows = cmsBlogMapper.getBlogCountById(blog.getId());
        if (0 == rows){
            return ResultGenerator.genFailResult(ResultCode.BLOG_NOT_EXIST,"??????????????????????????????");
        }

        blog.setUpdatedAt(new Date());

        if (blog.getBlogImagesList().size() > 0){
            //?????????????????????????????????
            cmsBlogImagesMapper.batchDeleteBlogImages(blog.getId());
            //?????????????????????????????????
            for (BlogImages blogImages:blog.getBlogImagesList()){
                blogImages.setBlogId(blog.getId());
            }
            cmsBlogImagesService.batch(blog.getBlogImagesList());
        }

        rows = updateRows(blog);
        if (0 == rows){
            return ResultGenerator.genFailResult(ResultCode.BLOG_UPDATE_ERROR,"????????????????????????????????????");
        }else {

            List<BlogTag> blogTagList = new ArrayList<BlogTag>();
            BlogTag blogTag;
            //??????????????????
            if (null != blog.getTagName()){
                String[] array = blog.getTagName().split(",");
                for (int i = 0;i < array.length; i++){
                    //
                    List<String> stringList = cmsBlogTagService.selectAllTag();
                    //??????????????????????????????????????????
                    if (!stringList.contains(array[i])){
                        blogTag = new BlogTag();
                        blogTag.setIsDelete(false);
                        blogTag.setCreatedAt(new Date());
                        blogTag.setTagName(array[i]);
                        blogTagList.add(blogTag);
                    }
                }
            }
            //?????????????????????????????????????????????
            if (blogTagList.size() > 0){
                cmsBlogTagService.save(blogTagList);
            }

            Result result= ResultGenerator.genSuccessResult();
            result.setData(blog);
            return result;
        }
    }
}
