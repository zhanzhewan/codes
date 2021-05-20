package com.restkeeper.controller;

import com.aliyun.oss.OSSClient;
import com.restkeeper.utils.Result;
import com.restkeeper.utils.ResultCode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Api(tags = {"图片上传通用接口"})
@RestController
@RefreshScope
public class FileUploadController {

    @Autowired
    private OSSClient ossClient;

    @Value("${bucketName}")
    private String bucketName;

    @Value("${spring.cloud.alicloud.oss.endpoint}")
    private String endpoint;

    @PostMapping("/fileUpload")
    public Result fileUpload(@RequestParam("file") MultipartFile multipartFile){

        Result result = new Result();

        //设置图片上传的文件名称
        String fileName = System.currentTimeMillis()+"_"+multipartFile.getOriginalFilename();

        //执行图片上传
        try {
            ossClient.putObject(bucketName,fileName,multipartFile.getInputStream());

            //拼接文件路径进行返回
            String logoPath = "https://"+bucketName+"."+endpoint+"/"+fileName;

            result.setData(logoPath);
            result.setStatus(ResultCode.success);
            result.setDesc("上传成功");
            return result;

        } catch (IOException e) {
            e.printStackTrace();
            result.setStatus(ResultCode.error);
            result.setDesc("上传失败");
            return result;
        }
    }

    @PostMapping("/imageUploadResize")
    @ApiImplicitParam(paramType = "form", dataType = "file", name = "fileName", value = "上传文件", required = true)
    public Result imageUploadResize(@RequestParam("fileName") MultipartFile multipartFile){

        Result result = new Result();

        String fileName = System.currentTimeMillis()+"_"+multipartFile.getOriginalFilename();

        //图片上传
        try {
            ossClient.putObject(bucketName,fileName,multipartFile.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(ResultCode.error);
            result.setDesc("图片上传失败");
            return result;
        }

        String imagePath = "https://"+bucketName+"."+endpoint+"/"+fileName+"?x-oss-process=image/resize,m_fill,h_100,w_200";

        result.setStatus(ResultCode.success);
        result.setDesc("图片上传成功");
        result.setData(imagePath);

        return result;
    }
}
