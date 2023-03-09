package com.springboot.catdemo.studyDemo.aop.dynamicProxy;

import org.springframework.transaction.annotation.Transactional;

/**
 * 抽象对象
 * 抽象接口，描述服务提供者的行为
 */
public interface ManToolsFactory {

    void saleManTools(String size);
}
