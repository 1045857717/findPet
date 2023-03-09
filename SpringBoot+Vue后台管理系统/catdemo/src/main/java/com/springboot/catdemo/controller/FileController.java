package com.springboot.catdemo.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.StrPool;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.springboot.catdemo.common.Constants;
import com.springboot.catdemo.common.Result;
import com.springboot.catdemo.entity.Files;
import com.springboot.catdemo.mapper.FileMapper;
import com.springboot.catdemo.service.impl.FileServiceImpl;
import com.springboot.catdemo.utils.MyRedisUtils;
import com.springboot.catdemo.utils.TokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件上传相关接口
 *
 * @Author: can
 * @Description:
 * @Date: Create in 6:37 2022/3/16
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${files.upload.path}")
    private String fileUploadPath;

    @Resource
    private FileMapper fileMapper;

    @Autowired
    private FileServiceImpl fileService;

    /**
     * 文件上传链接
     *
     * @param file 前端传递过来的文件
     * @return
     * @throws IOException
     */
    @PostMapping("/upload")
    @Transactional(rollbackFor = {Exception.class})
    public String upload(@RequestParam MultipartFile file, HttpServletRequest request) throws IOException {

        // 获取url协议(例如:http)
        String serverScheme = request.getScheme();
        // 获取url地址 https://www.jb51.net/article/71693.htm
        String serverName = request.getServerName();
        // 获取url端口
        int serverPort = request.getServerPort();
        // 生成下载文件的接口
        String url = serverScheme + "://" + serverName + ":" + serverPort;
        System.out.println(url);

        // 获取文件的名称
        String originalFilename = file.getOriginalFilename();
        // 获取文件的类型
        String type = FileUtil.extName(originalFilename);
        // 获取文件的字节(b)
        long fileSize = file.getSize();
        // 定义一个文件唯一的标识码
        String uuid = IdUtil.fastSimpleUUID();
        // 生成的文件名
        String fileUUID = uuid + StrUtil.DOT + type;
        // 文件下载的完整路径
        String fullPath = fileUploadPath + fileUUID;
        // 创建文件信息
        File uploadFile = new File(fullPath);
        // 判断 配置的文件目录 是否存在 并生成文件夹
        File parentFile = uploadFile.getParentFile();
        if (!(parentFile.exists())) {
            parentFile.mkdirs();
        }

        // 生成文件的唯一MD5
        String md5 = "";
        // 上传文件到指定目录
        file.transferTo(uploadFile);
        // 上传文件后才能获取md5,通过对比md5避免重复上传相同的文件
        md5 = SecureUtil.md5(uploadFile);
        // 通过md5判断 从数据库查询 是否有相同的md5
        Files files = getFileByMd5(md5);
        // 如果文件已经存在数据库中，则不需要再保存到磁盘目录
        if (files != null) {
            // 通过文件md5判断已存在 获取文件原本的下载接口
            url = files.getUrl();
            // 由于文件已存在，所以删除刚才上传的重复文件
            uploadFile.delete();
        } else { // 数据库中不存在重复文件，则不删除刚才上传的文件
            // 生成下载文件的接口
            url = url + StrPool.C_SLASH + "file" + StrPool.C_SLASH + fileUUID;
        }
        // 相关信息存储到数据库
        Files saveFile = new Files()
                .setName(originalFilename)
                .setType(type)
                .setSize(fileSize / 1024) // KB
                .setUrl(url)
                .setMd5(md5)
                .setCreateUsername(TokenUtils.getCurrentUser().getUsername());
        fileMapper.insert(saveFile);
        // 清空缓存
        MyRedisUtils.deleteKey(Constants.FILES_KEY);
        return url;
    }

    /**
     * 文件下载接口  http://localhost:9090/file/ + {fileUUID}
     *
     * @param fileUUID
     * @param response
     * @throws IOException
     */
    @GetMapping("/{fileUUID}")
    public void download(@PathVariable String fileUUID, HttpServletResponse response) throws IOException {
        // 设置输出流格式
        response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileUUID, CharsetUtil.UTF_8));
        response.setContentType("application/octet-stream");

        ServletOutputStream out = response.getOutputStream();
        // 根据文件的唯一标识码获取文件
        File uploadFile = new File(fileUploadPath + fileUUID);
        // 读取文件所有数据
        byte[] bytes = FileUtil.readBytes(uploadFile);
        out.write(bytes);
        out.flush();
        out.close();
    }

    /**
     * 删除文件
     *
     * @param id
     * @return
     */
    @DeleteMapping(value = "/{id}")
    @Transactional(rollbackFor = {Exception.class})
    public Result delete(@PathVariable Integer id) {
        Files file = fileMapper.selectById(id);
        file.setIsDelete(true);
        fileMapper.updateById(file);
        // 清空缓存
        MyRedisUtils.deleteKey(Constants.FILES_KEY);
        return Result.success();
    }

    /**
     * 批量删除文件
     *
     * @param ids
     * @return
     */
    @PostMapping(value = "/del/batch")
    public Result deleteBatch(@RequestBody List<Integer> ids) {
        List<Files> files = fileMapper.selectBatchIds(ids).stream()
                .map(file -> file.setIsDelete(true))
                .collect(Collectors.toList());
        fileService.updateBatchById(files);
        // 清空缓存
        MyRedisUtils.deleteKey(Constants.FILES_KEY);
        return Result.success();
    }

    /**
     * 修改文件状态(启用：未启用)
     *
     * @param files
     * @return
     */
    @PostMapping(value = "/updateEnable")
    public Result updateEnable(@RequestBody Files files) {
        if (files != null) {
            boolean result = fileService.updateById(files);
            if (result) {
                // 清空缓存
                MyRedisUtils.deleteKey(Constants.FILES_KEY);
            }
            return Result.success(result);
        } else {
            return Result.error(Constants.CODE_400, "参数错误");
        }
    }

    /**
     * 根据Id查询文件
     *
     * @param id
     * @return
     */
    @GetMapping(value = "/videoDetail/{id}")
    public Result getFileById(@PathVariable Integer id) {
        Files file = fileMapper.selectById(id);
        return Result.success(file);
    }

    /**
     * 分页查询文件
     *
     * @param pageNum  当前页
     * @param pageSize 总页数
     * @param name     文件名
     * @return
     */
    @GetMapping(value = "/page")
    public Result findPage(@RequestParam Integer pageNum,
                           @RequestParam Integer pageSize,
                           @RequestParam(defaultValue = "") String name,
                           @RequestParam(defaultValue = "") String type) {
        QueryWrapper<Files> queryWrapper = new QueryWrapper<>();
        // 查询状态为 未删除的数据(tinyint:0为false，1为true)
        queryWrapper.eq("is_delete", false);
        if (name != null && !"".equals(name))
            queryWrapper.like("name", name);
        if (name != null && !"".equals(type))
            queryWrapper.like("type", type);
        queryWrapper.orderByDesc("id");
        return Result.success(fileMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper));
    }

    /**
     * 从缓存中获取所有文件
     */
    @GetMapping("/cacheAll")
    public Result getCacheFileAll() {
        // 从缓存中获取文件数据
//        String jsonStr = stringRedisTemplate.opsForValue().get(Constants.FILES_KEY);
        String jsonStr = (String) MyRedisUtils.getStringCache(Constants.FILES_KEY);
        List<Files> files;
        // 缓存中jsonStr为空
        if (StrUtil.isBlank(jsonStr)) {
            // 从数据库中查询文件数据
            QueryWrapper<Files> queryWrapper = new QueryWrapper<>();
            // 查询状态为 未删除的数据(tinyint:0为false，1为true)
            queryWrapper.eq("is_delete", false);
            // 查询状态为 是否禁用链接(0:禁用,1:可用|tinyint:0为false，1为true)
            queryWrapper.eq("enable", true);
            files = fileMapper.selectList(queryWrapper);
            // 存到缓存中
            MyRedisUtils.setStringCache(Constants.FILES_KEY, JSONUtil.toJsonStr(files));
//            stringRedisTemplate.opsForValue().set(Constants.FILES_KEY, JSONUtil.toJsonStr(files));
        } else {
            // redis缓存中取出文件数据
//            files = JSONUtil.toBean(jsonStr, new TypeReference<List<Files>>() {}, true);
            files = JSONUtil.toList(jsonStr, Files.class);

        }
        return Result.success(files);
    }

    /**
     * 获取数据库中存在的所有文件类型
     *
     * @return
     */
    @GetMapping(value = "getFileTypes")
    public List<String> getFileTypes() {
        QueryWrapper<Files> fileQuery = new QueryWrapper<>();
        fileQuery.select("type").groupBy("type");
        List<String> fileTypes = fileService.list(fileQuery).stream().map(Files::getType).collect(Collectors.toList());
//        List<String> fileTypes = fileService.list().stream().collect(
//                Collectors.collectingAndThen(
//                        Collectors.toCollection(() -> new TreeSet<>(
//                                Comparator.comparing(Files::getType))), ArrayList::new))
//                .stream().map(Files::getType).collect(Collectors.toList());
        return fileTypes;
    }

    /**
     * 通过文件的md5查询文件
     *
     * @param md5
     * @return
     */
    private Files getFileByMd5(String md5) {
        QueryWrapper<Files> query = new QueryWrapper<>();
        query.eq("md5", md5);
        // 通过md5判断文件是否存在
        List<Files> filesList = fileMapper.selectList(query);
        return (filesList != null && filesList.size() > 0) ? filesList.get(0) : null;
    }
}
