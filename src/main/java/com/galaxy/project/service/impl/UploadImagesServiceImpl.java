package com.galaxy.project.service.impl;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.galaxy.project.core.AbstractService;
import com.galaxy.project.core.Result;
import com.galaxy.project.core.ResultCode;
import com.galaxy.project.core.ResultGenerator;
import com.galaxy.project.dao.UploadImagesMapper;
import com.galaxy.project.model.Images;
import com.galaxy.project.param.ZipParam;
import com.galaxy.project.service.UploadImagesService;
import com.galaxy.project.utils.DigitUtil;
import com.galaxy.project.utils.ImageUtil;
import com.galaxy.project.utils.Logger;
import com.galaxy.project.utils.ZipMultiFileUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class UploadImagesServiceImpl extends AbstractService<Images> implements UploadImagesService {

    @Value("${galaxy.amazonProperties.imageBucketName}")
    private String imageBucketName;

    @Value("${galaxy.amazonProperties.image360BucketName}")
    private String image360BucketName;

    //Linux文件下固定路径/home/ec2-user/targetLogoFile.png
    //Windows文件下固定路径D:\\targetLogoFile .png

    //private static String markImg = Constant.OS_PREFIX;

    private static String markImg = "logo.png";

    private static String notLogo = "notLogo.tmp";

    @Autowired
    private UploadImagesMapper uploadImagesMapper;

    @Autowired
    private AmazonS3 amazonS3Client;

    public InputStream download(String key){
        S3Object object = amazonS3Client.getObject(new GetObjectRequest(imageBucketName, key));
        return object.getObjectContent();
    }

    @Override
    public Result uploadImages(MultipartFile multipartFile, String title, String description, String suffix, String level, Integer status, String statusName) {

        if (multipartFile.isEmpty()){
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_NOT_EXIST,"文件不存在");
        }

        if(title == null) {
            title = "title";
        }
        if(description == null) {
            description = "description";
        }
        if(suffix == null) {
            suffix = "ex";
        }
        if(level == null) {
            level = "star";
        }

        Images images = new Images();
        images.setCreatedAt(new Date());
        images.setTitle(title);
        images.setDescription(description);
        images.setSuffix(suffix);
        images.setLevel(level);
        images.setImageName(multipartFile.getOriginalFilename());
        images.setContentType(multipartFile.getContentType());
        images.setSize(multipartFile.getSize());
        images.setStatusName(statusName);

        if ("exterior".equals(statusName)){
            images.setRating(100L);
            images.setTmpRating(10000L);
        }else {
            images.setRating(0L);
            images.setTmpRating(10000L);
        }

        try{
            //添加图片水印
            File file = ImageUtil.addPicMarkToMutipartFile(multipartFile, markImg);

            if (null == file){
                return ResultGenerator.genFailResult(ResultCode.IMAGEAS_LOGO_ERROR,"增加Logo错误，请重新上传图片");
            }

            String bucketName = new String();

            //业务状态(1普通图片:2为360°图片)
            if ("360".equals(statusName)){
                bucketName = image360BucketName;
                images.setStatus(2);
            }else if ("interior".equals(statusName)){
                bucketName = imageBucketName;
                images.setStatus(3);
            }else if ("exterior".equals(statusName)){
                bucketName = imageBucketName;
                images.setStatus(4);
            } else {
                bucketName = imageBucketName;
                images.setStatus(1);
            }

            //上传图片
            S3Object s3Object240 = uploadFileToS3Bucket(bucketName, file);
            //图片桶路径
            images.setS3Key240(s3Object240.getKey());
            //图片全路径
            images.setObjectUrl240("https://" + bucketName + ".s3-us-west-1.amazonaws.com/" + s3Object240.getKey());

            //生成缩略图
            //获取要加logo得图片
            InputStream inputImg = multipartFile.getInputStream();
            Image img = ImageIO.read(inputImg);
            int imgWidth = img.getWidth(null);
            int imgHeight = img.getHeight(null);
            int wDiv = imgWidth/600;
            int hDiv = imgHeight/600;
            int div = (wDiv+hDiv)/2;
            if (0 == div){
                div = 1;
            }
            // int div = 2;
            File small = changSmall(file,imgWidth/div,imgHeight/div);

            //上传缩略图
            S3Object s3SmallObject240 = uploadFileToS3Bucket(bucketName, small);
            //图片全路径
            images.setSmallObjectUrl240("https://" + bucketName + ".s3-us-west-1.amazonaws.com/" + s3SmallObject240.getKey());

            // 删除临时文件
            file.delete();
            small.delete();
            inputImg.close();
            img.flush();
            s3Object240.close();
            s3SmallObject240.close();
            save(images);
            // explicitly gc after each upload
            System.gc();
            return ResultGenerator.genSuccessResult(images);
        }catch (Exception e){
            Logger.info(this,e.getMessage());
            e.printStackTrace();
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_ERROR,"上传图片失败" + e);
        }
    }

    /**
     * 将指定图片在指定 位置生成缩略图
     */
    private File changSmall(File uploadFile,int imgWidth,int imgHeight){
        InputStream inputImg = null;
        try {
            inputImg = new FileInputStream(uploadFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Image img = null;
        try {
            img = ImageIO.read(inputImg);
            BufferedImage bufImg = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
            //取到画笔
            Graphics2D g = bufImg.createGraphics();
            //画底片
            g.drawImage(img, 0, 0, bufImg.getWidth(), bufImg.getHeight(), null);
            g.dispose();
            File outFile = new File("small_pic.png");
            ImageIO.write(bufImg, "png", outFile);//写图片

            // 把文件flush下 不然会占空间
            img.flush();
            bufImg.flush();
            inputImg.close();
            return outFile;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Result uploadImagesUrl(MultipartFile multipartFile) {

        if (null == multipartFile){
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_NOT_EXIST,"文件不存在");
        }

        if (multipartFile.isEmpty()){
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_NOT_EXIST,"文件不存在");
        }

        try{
            //添加图片水印
            File file = ImageUtil.addPicMarkToMutipartFile(multipartFile, markImg);

            if (null == file){
                return ResultGenerator.genFailResult(ResultCode.IMAGEAS_LOGO_ERROR,"增加Logo错误，请重新上传图片");
            }

            //上传图片
            S3Object s3Object240 = uploadFileToS3Bucket(imageBucketName, file);
            //删除临时文件
            file.delete();
            return ResultGenerator.genSuccessResult("https://" + "galaxy-image" + ".s3-us-west-1.amazonaws.com/" + s3Object240.getKey());
        }catch (Exception e){
            e.printStackTrace();
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_ERROR,"上传图片失败");
        }
    }

    @Override
    public Result uploadImagesDownload(MultipartFile multipartFile) {
        if (multipartFile.isEmpty()){
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_NOT_EXIST,"文件不存在");
        }

        try{
            //添加图片水印
            File file = ImageUtil.addPicMarkToMutipartFile(multipartFile, markImg);

            if (null == file){
                return ResultGenerator.genFailResult(ResultCode.IMAGEAS_LOGO_ERROR,"增加Logo错误，请重新上传图片");
            }

            //上传图片
            S3Object s3Object240 = uploadFileToS3Bucket(imageBucketName, file);

            //下载图片到本地
            saveImg("https://" + "galaxy-image" + ".s3-us-west-1.amazonaws.com/" + s3Object240.getKey());

            //删除临时文件
            file.delete();
            s3Object240.close();
            return ResultGenerator.genSuccessResult("下载图片地址 D:\\out.png");
        }catch (Exception e){
            e.printStackTrace();
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_ERROR,"上传图片失败");
        }
    }

    @Override
    public Result downloadImage(String imageName, HttpServletRequest request, HttpServletResponse response) {
        String fileUrl = imageName;
        if (fileUrl != null) {
            //当前是从该工程的WEB-INF//File//下获取文件(该目录可以在下面一行代码配置)然后下载到C:\\users\\downloads即本机的默认下载的目录
           /* String realPath = request.getServletContext().getRealPath(
                    "//WEB-INF//");*/
            /*File file = new File(realPath, fileName);*/
            File file = new File(fileUrl);
            if (file.exists()) {
                //response.setContentType("application/force-download");// 设置强制下载不打开
                response.addHeader("Content-Disposition", "attachment;fileName=" + imageName);// 设置文件名
                byte[] buffer = new byte[1024];
                FileInputStream fis = null;
                BufferedInputStream bis = null;
                try {
                    fis = new FileInputStream(file);
                    bis = new BufferedInputStream(fis);
                    OutputStream os = response.getOutputStream();
                    int i = bis.read(buffer);
                    while (i != -1) {
                        os.write(buffer, 0, i);
                        i = bis.read(buffer);
                    }
                    System.out.println("success");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (bis != null) {
                        try {
                            bis.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return ResultGenerator.genFailResult(ResultCode.FILE_DOWNLOAD_ERROR,"文件下载失败");
    }

    @Override
    public Result testUploadImages(MultipartFile multipartFile) {
        if (multipartFile.isEmpty()){
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_NOT_EXIST,"文件不存在");
        }

        try{

            //添加图片水印
            File file = ImageUtil.addPicMarkToMutipartFile(multipartFile, markImg);

            if (null == file){
                return ResultGenerator.genFailResult(ResultCode.IMAGEAS_LOGO_ERROR,"增加Logo错误，请重新上传图片");
            }

            //上传图片
            S3Object s3Object240 = uploadFileToS3Bucket(imageBucketName, file);

            saveImg("https://" + "galaxy-image" + ".s3-us-west-1.amazonaws.com/" + s3Object240.getKey());

            //删除临时文件
            file.delete();
            s3Object240.close();
            return ResultGenerator.genSuccessResult("https://" + "galaxy-image" + ".s3-us-west-1.amazonaws.com/" + s3Object240.getKey());
        }catch (Exception e){
            e.printStackTrace();
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_ERROR,"上传图片失败");
        }
    }

    @Override
    public Result uploadImagesNotLogo(MultipartFile multipartFile) {
        if (null == multipartFile){
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_NOT_EXIST,"文件不存在");
        }

        try{
            InputStream inputImg = multipartFile.getInputStream();
            Image img = ImageIO.read(inputImg);
            int imgWidth = img.getWidth(null);
            int imgHeight = img.getHeight(null);
            BufferedImage bufImg = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
            //取到画笔
            Graphics2D g = bufImg.createGraphics();
            //画底片
            g.drawImage(img, 0, 0, bufImg.getWidth(), bufImg.getHeight(), null);
            g.dispose();
            File outFile = new File("out_pic.png");
            ImageIO.write(bufImg, "png", outFile);//写图片

            if (null == outFile){
                return ResultGenerator.genFailResult(ResultCode.IMAGEAS_LOGO_ERROR,"增加Logo错误，请重新上传图片");
            }

            //上传图片
            S3Object s3Object240 = uploadFileToS3Bucket(imageBucketName, outFile);
            //删除临时文件
            outFile.delete();
            inputImg.close();
            return ResultGenerator.genSuccessResult("https://" + "galaxy-image" + ".s3-us-west-1.amazonaws.com/" + s3Object240.getKey());
        }catch (Exception e){
            e.printStackTrace();
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_ERROR,"上传图片失败");
        }
    }

    @Override
    public Result findByModalData(Integer page, Integer size, String title) {
        PageHelper.startPage(page, size);
        Images images = new Images();
        if ("{}".equalsIgnoreCase(title)){
            images.setTitle("");
        }else {
            images.setTitle(title);
        }
        List<Images> list = uploadImagesMapper.list(images);
        PageInfo pageInfo = new PageInfo(list);
        return ResultGenerator.genSuccessResult(pageInfo);
    }

    @Override
    public Result batchUploadImages(MultipartFile[] multipartFile, String title, String description, String suffix, String level, Integer status, String statusName) {
        if (multipartFile.length==0){
            return ResultGenerator.genFailResult(ResultCode.FILEUPLOAD_ERROR,"上传文件不可为空");
        }
        List<Images> imagesList = new ArrayList<Images>();
        Images images;
        for (MultipartFile file : multipartFile){
            Result result = uploadImages(file,title,description,suffix,level,status,statusName);
            images = (Images) result.getData();
            imagesList.add((images));
        }
        return ResultGenerator.genSuccessResult(imagesList);
    }

    @Override
    public Result detail(Long id) {
        Images images = findById(id);
        if (null != images){
            //主要积分点击+1
            images.getRating();
            BigDecimal result = images == null ?  BigDecimal.ZERO : BigDecimal.valueOf(images.getRating());
            images.setRating(Long.valueOf(result.add(new BigDecimal("1")).toString()));
            update(images);
        }
        return ResultGenerator.genSuccessResult(images);
    }

    @Override
    public Result downloadZip(List<ZipParam> paramList) {
        if (paramList.size() > 0){
            //全部下载到本地的图片
            List<String> urlList = new ArrayList<>();
            //转换为File文件
            List<File> fileList = new ArrayList<>();
            for (ZipParam d : paramList) {
                String imgUrl = downloadImg(d.getImgUrl());
                fileList.add(new File(imgUrl));
                urlList.add(imgUrl);
            }

            String url =  "D:\\" + "galaxy" + DigitUtil.generatorLongId() + ".zip";
            File zipFile = new File(url);
            // 调用压缩方法
            ZipMultiFileUtil.zipFiles(fileList.stream().toArray(File[]::new), zipFile);

            //删除临时文件
            if (fileList.size() > 0){
                for (File d:fileList) {
                    d.delete();
                }
            }

            //删除图片
            //zipFile.delete();

        }
        return ResultGenerator.genSuccessResult();
    }

    /**
     * 根据url下载图片到本地
     * @param imgeUrl 图片的路径
     */
    public String downloadImg(String imgeUrl) {
        BufferedImage bufferedImage = null;
        try {
            URL url=new URL(imgeUrl);
            URLConnection urlConnection=url.openConnection();
            HttpURLConnection httpURLConnection=(HttpURLConnection)urlConnection;
            httpURLConnection.connect();
            if (httpURLConnection.getResponseCode()== HttpURLConnection.HTTP_OK){
                InputStream inputStream=httpURLConnection.getInputStream();

                bufferedImage= ImageIO.read(inputStream);
                //参数二设置保存图片的格式
                //参数三设置图片保存地址
                //ImageIO.write(bufferedImage,"png",new File("D:\\out.png")); 下载到本地固定路径
                //ImageIO.write(bufferedImage,"png",new File(DigitUtil.generatorLongId() + ".png")); 下载到项目下
                String imgUrl = DigitUtil.generatorLongId() + ".png";
                ImageIO.write(bufferedImage,"png",new File(imgUrl));

                // flush buffered files
                bufferedImage.flush();
                return imgUrl;
            }else {
                System.out.println("连接失败");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 根据url下载图片到本地
     * @param imgeUrl 图片的路径
     */
    public Result saveImg(String imgeUrl) {
        BufferedImage bufferedImage = null;
        try {
            URL url=new URL(imgeUrl);
            URLConnection urlConnection=url.openConnection();
            HttpURLConnection httpURLConnection=(HttpURLConnection)urlConnection;
            httpURLConnection.connect();
            if (httpURLConnection.getResponseCode()== HttpURLConnection.HTTP_OK){
                InputStream inputStream=httpURLConnection.getInputStream();

                bufferedImage= ImageIO.read(inputStream);
                //参数二设置保存图片的格式
                //参数三设置图片保存地址
                //ImageIO.write(bufferedImage,"png",new File("D:\\out.png")); 下载到本地固定路径
                //ImageIO.write(bufferedImage,"png",new File(DigitUtil.generatorLongId() + ".png")); 下载到项目下
                ImageIO.write(bufferedImage,"png",new File(DigitUtil.generatorLongId() + ".png"));

                // flush buffered files
                bufferedImage.flush();
                return ResultGenerator.genSuccessResult();
            }else {
                System.out.println("连接失败");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] readInputStream(InputStream inStream) throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        // flush() and close()
        inStream.close();
        // 把outStream里的数据写入内存
        try{
            return outStream.toByteArray();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
             outStream.flush();
             outStream.close();
        }
        // return outStream.toByteArray();
        return outStream.toByteArray();
    }

    /**
     * 根据url获取图片的路径
     */
    private String getImgPath(String imgUrl) {
        return imgUrl.substring(imgUrl.indexOf("n/") + 1, imgUrl.lastIndexOf("/"));
    }

    /**
     * 根据url获取图片的名称
     */
    private String getImgFileName(String imgUrl) {
        return imgUrl.substring(imgUrl.lastIndexOf("/") + 1);
    }

    @Override
    public Result updateImages(Images images) {
        if(images.getTitle() == null) {
            images.setTitle("title");
        }
        if(images.getDescription() == null) {
            images.setDescription("description");
        }
        if(images.getSuffix() == null) {
            images.setSuffix("ex");
        }
        if(images.getLevel() == null) {
            images.setLevel("star");
        }
        images.setUpdatedAt(new Date());
        update(images);
        Result result = ResultGenerator.genSuccessResult();
        result.setData(images);
        return result;
    }

    @Override
    public Result delete(Long id) {

        Images images =  uploadImagesMapper.getImagesDetailsById(id);
        if (null == images){
            return ResultGenerator.genFailResult(ResultCode.IMAGEAS_NULL_ERROR,"图片不存在或者已删除");
        }

        images.setId(id);
        images.setIsDelete(true);
        update(images);

        //删除远程图片
        amazonS3Client.deleteObject(new DeleteObjectRequest(imageBucketName, images.getS3Key240()));

        return ResultGenerator.genSuccessResult();
    }

    @Override
    public Result list(Integer page, Integer size, Images images) {
        PageHelper.startPage(page, size);
        List<Images> list = uploadImagesMapper.list(images);
        PageInfo pageInfo = new PageInfo(list);
        return ResultGenerator.genSuccessResult(pageInfo);
    }

    @Override
    public List<Images> imagesList(Images images) {
        List<Images> list = uploadImagesMapper.list(images);
        return list;
    }


    /**
     * 上传到亚马逊上去
     * @param bucketName
     * @param file
     * @return
     */
    public S3Object uploadFileToS3Bucket(final String bucketName, final File file) {
        final String objectKey = LocalDateTime.now() + "_" + file.getName();
        System.out.println("Uploading file with name= " + objectKey);
        final PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, file);
        PutObjectResult putObjectResult = amazonS3Client.putObject(putObjectRequest);
        return amazonS3Client.getObject(bucketName, objectKey);
    }
}
