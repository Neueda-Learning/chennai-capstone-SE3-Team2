package com.yellow.trade.strategy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** This module's mappers, scanned here so the module carries its own wiring. */
@Configuration
@MapperScan(basePackageClasses = StrategyMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class StrategyConfig {
}
