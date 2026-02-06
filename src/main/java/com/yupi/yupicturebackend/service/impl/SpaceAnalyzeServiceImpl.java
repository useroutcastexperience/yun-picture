package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.model.dto.space.analyze.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.space.analyze.*;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceAnalyzeService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceAnalyzeService {
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private PictureService pictureService;
    /**
     *  检查空间分析权限
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser){
        // 检查权限
        if(spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()){
            // 全空间分析或者公共空间分析需要管理员权限
            ThrowUtils.throwIf(!userService.isAdmin(loginUser),ErrorCode.NO_AUTH_ERROR,"无权访问公共图库");
        }
        else{
            // 私有空间权限校验
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null||spaceId <0 ,ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null ,ErrorCode.NO_AUTH_ERROR,"空间不存在");
            spaceService.checkSpaceAuth(loginUser,space);
        }
    }

    /**
     *  填充空间分析查询条件
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper){
        if(spaceAnalyzeRequest.isQueryAll()){
            return ;
        }
        if(spaceAnalyzeRequest.isQueryPublic()){
            queryWrapper.isNull("spaceId");
            return ;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if(spaceId != null){
            queryWrapper.eq("spaceId",spaceId);
            return ;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR,"未指定查询范围");

    }


    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(spaceAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        if(spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()){
            // 查询公共空间或者全部空间
            // 只有管理员可以查询全部空间或者公共空间，校验权限
            ThrowUtils.throwIf(!userService.isAdmin(loginUser),ErrorCode.NO_AUTH_ERROR,"无权访问公共图库");
            // 统计公共空间的使用情况
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            if(!spaceAnalyzeRequest.isQueryAll())
            {
                queryWrapper.isNull("spaceId");
            }
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            long usedSize = pictureObjList.stream().mapToLong(result ->result instanceof Long ? (Long) result : 0).sum();
            long usedCount = pictureObjList.size();
            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeReponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeReponse.setUsedSize(usedSize);
            spaceUsageAnalyzeReponse.setUsedCount(usedCount);
            // 公共图库无上限，无比例
            spaceUsageAnalyzeReponse.setMaxSize(null);
            spaceUsageAnalyzeReponse.setCountUsageRatio(null);
            spaceUsageAnalyzeReponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeReponse.setMaxCount(null);
            return spaceUsageAnalyzeReponse;
        }
        else {
            // 查询私有空间
            // 查询指定空间
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null||spaceId <0 ,ErrorCode.PARAMS_ERROR);
            // 获取空间信息
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null,ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            // 只有空间所有者和管理员可以访问
            spaceService.checkSpaceAuth(loginUser,space);
            // 构造返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeReponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeReponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeReponse.setUsedCount(space.getTotalCount());
            spaceUsageAnalyzeReponse.setMaxSize(space.getMaxSize());
            spaceUsageAnalyzeReponse.setMaxCount(space.getMaxCount());
            // 后端直接算好百分比，这样前端就可以直接展示了
            spaceUsageAnalyzeReponse.setSizeUsageRatio(space.getTotalSize()*100.0/space.getMaxSize());
            spaceUsageAnalyzeReponse.setCountUsageRatio(space.getTotalCount()*100.0/space.getMaxCount());
            return spaceUsageAnalyzeReponse;
        }
    }
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查权限
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 根据分析范围补充查询条件
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);

        // 使用 MyBatis-Plus 分组查询
        queryWrapper.select("category AS category",
                        "COUNT(*) AS count",
                        "SUM(picSize) AS totalSize")
                .groupBy("category");

        // 查询并转换结果
        return pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result -> {
                    String category = result.get("category") != null ? result.get("category").toString() : "未分类";
                    Long count = ((Number) result.get("count")).longValue();
                    Long totalSize = ((Number) result.get("totalSize")).longValue();
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                })
                .collect(Collectors.toList());
    }
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);

        // 查询所有符合条件的标签
        queryWrapper.select("tags");
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull) //等价于obj -> ObjUtil.isNotNull(obj)
                .map(Object::toString) //将每个对象转换为字符串
                .collect(Collectors.toList());

        // 合并所有标签并统计使用次数
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        // 转换为响应对象，按使用次数降序排序
        return tagCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 降序排列
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null , ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("picSize");
        // 补全查询范围
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);
        // 查询符合条件的图片
        List<Long> picSizes = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .map(size -> ((Number)size).longValue())
                .collect(Collectors.toList());

        // 按图片大小范围分类
        Map<String,Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB",picSizes.stream().filter(size ->size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB",picSizes.stream().filter(size ->size >=100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB",picSizes.stream().filter(size ->size >=500 * 1024 && size < 1024 * 1024).count());
        sizeRanges.put(">1MB",picSizes.stream().filter(size ->size >=1024 * 1024 && size < 5 * 1024 * 1024).count());
        // 转换为响应对象
        return sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        // 分析维度：每日，每月，每周
        // 获取时间维度
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch(timeDimension)
        {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime,'%Y-%m-%d') AS period ", "COUNT(*) AS count");
                break;
            case "week":
                queryWrapper.select("WEEKOFYEAR(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                queryWrapper.select("MONTH(createTime,'%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"不支持该时间维度");

        }
        // 分组和排序
        queryWrapper.groupBy("period").orderByAsc("period");
        // 查询结果并转换
        List<SpaceUserAnalyzeResponse> result = pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(map -> new SpaceUserAnalyzeResponse(map.get("period").toString(), ((Number) map.get("count")).longValue()))
                .collect(Collectors.toList());
        return result;
    }
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser)
    {
        // 校验参数
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR,"无权查看空间排行");
        // 构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("LIMIT" + spaceRankAnalyzeRequest.getTopN());
        return spaceService.list(queryWrapper);
    }
}
