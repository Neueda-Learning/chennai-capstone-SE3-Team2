package com.yellow.trade.portfolio;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** This module's mappers, scanned here so the module carries its own wiring. */
@Configuration
@MapperScan(basePackageClasses = RealisedMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class PortfolioConfig {
}
