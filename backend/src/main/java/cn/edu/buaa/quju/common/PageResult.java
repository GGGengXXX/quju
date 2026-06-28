package cn.edu.buaa.quju.common;

import java.util.List;

/** 统一分页响应（对齐 AGENTS.md：total/page/size/list）。 */
public record PageResult<T>(long total, int page, int size, List<T> list) {
    public static <T> PageResult<T> of(long total, int page, int size, List<T> list) {
        return new PageResult<>(total, page, size, list);
    }
}
