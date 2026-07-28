package com.qgyun.hltgq.hltgqsite.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.qgyun.hltgq.hltgqsite.handler.LocalDateTimeTypeHandler;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.qgyun.hltgq.hltgqsite.mapper", sqlSessionFactoryRef = "primarySqlSessionFactory")
public class PrimaryDataSourceConfig {

    @Bean(name = "primaryDataSource")
    @Primary
    public DataSource primaryDataSource(
            @Value("${spring.datasource.primary.jdbc-url}") String url,
            @Value("${spring.datasource.primary.username}") String username,
            @Value("${spring.datasource.primary.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        // PostgreSQL 驱动支持 JDBC 4 isValid()，无需 connectionTestQuery
        return ds;
    }

    @Bean(name = "primarySqlSessionFactory")
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(@Qualifier("primaryDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeHandlers(new LocalDateTimeTypeHandler());

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.KINGBASE_ES));
        factory.setPlugins(interceptor);

        // 全局列名加双引号：KingbaseES 中大写列名必须加引号，否则会折叠为小写
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.getDbConfig().setColumnFormat("\"%s\"");
        factory.setGlobalConfig(globalConfig);

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(false);
        factory.setConfiguration(config);

        return factory.getObject();
    }

    @Bean(name = "primarySqlSessionTemplate")
    @Primary
    public SqlSessionTemplate primarySqlSessionTemplate(@Qualifier("primarySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
