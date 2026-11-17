package com.fyreader.service.service;

import com.fyreader.service.common.BusinessException;
import com.fyreader.service.entity.BookSource;
import com.fyreader.service.mapper.BookSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 书源元数据管理。原 App 侧书源规则（search/info/toc/content rule）已废弃。
 */
@Service
public class BookSourceService {

    private final BookSourceMapper bookSourceMapper;

    public BookSourceService(BookSourceMapper bookSourceMapper) {
        this.bookSourceMapper = bookSourceMapper;
    }

    public List<BookSource> list(boolean onlyEnabled) {
        List<BookSource> list = bookSourceMapper.selectAll(onlyEnabled);
        return list == null ? Collections.emptyList() : list;
    }

    @Transactional(rollbackFor = Exception.class)
    public BookSource create(BookSource source) {
        if (source == null || !StringUtils.hasText(source.getSourceName())) {
            throw BusinessException.badRequest("书源名称不能为空");
        }
        if (!StringUtils.hasText(source.getId())) {
            source.setId("src_" + UUID.randomUUID().toString().replace("-", ""));
        }
        if (!StringUtils.hasText(source.getSourceType())) {
            source.setSourceType("API");
        }
        if (source.getEnabled() == null) {
            source.setEnabled(1);
        }
        bookSourceMapper.insert(source);
        return bookSourceMapper.selectById(source.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public BookSource update(String id, BookSource source) {
        BookSource exists = bookSourceMapper.selectById(id);
        if (exists == null) {
            throw BusinessException.notFound("书源不存在: " + id);
        }
        source.setId(id);
        bookSourceMapper.update(source);
        return bookSourceMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        BookSource exists = bookSourceMapper.selectById(id);
        if (exists == null) {
            throw BusinessException.notFound("书源不存在: " + id);
        }
        bookSourceMapper.deleteById(id);
    }
}
