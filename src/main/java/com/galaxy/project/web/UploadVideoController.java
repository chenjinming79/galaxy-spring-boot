package com.galaxy.project.web;

import com.galaxy.project.core.Result;
import com.galaxy.project.core.ResultGenerator;
import com.galaxy.project.model.Video;
import com.galaxy.project.service.UploadVideoService;
import com.galaxy.project.utils.Logger;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;

@RestController
@RequestMapping(value = "/gateway/upload/video")
@Api(tags = {"/gateway/upload/video"}, description = "视频管理模块")
public class UploadVideoController {

    @Autowired
    private UploadVideoService uploadVideoService;

    @ApiOperation(value = "截取帧数图片", notes = "截取帧数图片")
    @RequestMapping(value = "/fetchFrame", method = RequestMethod.POST)
    public Result fetchFrame(@RequestParam String videofile, @RequestBody Video video){
        return uploadVideoService.fetchFrame(videofile,video);
    }

    @ApiOperation(value = "上传视频只返回url", notes = "上传视频只返回url")
    @RequestMapping(value = "/uploadVideoUrl", method = RequestMethod.POST)
    public Result uploadVideoUrl(@RequestBody MultipartFile multipartFile){
        return uploadVideoService.uploadVideoUrl(multipartFile);
    }

    @ApiOperation(value = "上传视频", notes = "上传视频")
    @RequestMapping(value = "/uploadVideo", method = {RequestMethod.POST, RequestMethod.GET})
    public Result uploadVideo(@RequestParam(value = "multipartFile") MultipartFile multipartFile,
                              @RequestParam(value = "title",required = false) String title,
                              @RequestParam(value="description",required = false) String description,
                              @RequestParam(value = "suffix",required = false) String suffix,
                              @RequestParam(value="level",required = false) String level,
                              @RequestParam(value="status",required = false) Integer status,
                              @RequestParam(value="statusName",required = false) String statusName){
        Logger.info(this, "/video/uploadVideo 上传视频接口入参--->");
        return uploadVideoService.uploadVideo(multipartFile,title,description,suffix,level,status,statusName);
    }

    @ApiOperation(value = "新增视频", notes = "新增视频")
    @RequestMapping(value = "/add", method = {RequestMethod.POST, RequestMethod.GET})
    public Result add(@RequestBody Video video) {
        video.setCreatedAt(new Date());
        video.setIsDelete(false);
        uploadVideoService.save(video);
        Result result= ResultGenerator.genSuccessResult();
        result.setData(video);
        return result;
    }

    @ApiOperation(value = "删除视频", notes = "删除视频")
    @RequestMapping(value = "/delete", method = {RequestMethod.POST, RequestMethod.GET})
    public Result delete(@RequestParam Long id) {
        return uploadVideoService.delete(id);
    }

    @ApiOperation(value = "修改视频", notes = "修改视频")
    @RequestMapping(value = "/update", method = {RequestMethod.POST, RequestMethod.GET})
    public Result update(@RequestBody Video video) {
        uploadVideoService.update(video);
        Result result=ResultGenerator.genSuccessResult();
        result.setData(video);
        return result;
    }

    @ApiOperation(value = "获取视频单个详情", notes = "获取视频单个详情")
    @RequestMapping(value = "/detail", method = {RequestMethod.POST, RequestMethod.GET})
    public Result detail(@RequestParam Long id) {
        Video video = uploadVideoService.findById(id);
        return ResultGenerator.genSuccessResult(video);
    }

    @ApiOperation(value = "分页查询视频(Data)", notes = "分页查询视频(Data)")
    @RequestMapping(value = "/findByModalData", method = RequestMethod.POST)
    public Result findByModalData(@RequestParam(defaultValue="1",required=false) Integer page,
                       @RequestParam(defaultValue="20",required=false) Integer size,
                       @RequestParam(required =false) String title) {
        return uploadVideoService.findByModalData(page,size,title);
    }

    @ApiOperation(value = "分页查询视频", notes = "分页查询视频")
    @RequestMapping(value = "/findByModal", method = {RequestMethod.POST, RequestMethod.GET})
    public Result list(@RequestParam(defaultValue="1",required=false) Integer page,
                       @RequestParam(defaultValue="20",required=false) Integer size,
                       @RequestBody(required =false) Video video) {
        return uploadVideoService.list(page,size,video);
    }

}
