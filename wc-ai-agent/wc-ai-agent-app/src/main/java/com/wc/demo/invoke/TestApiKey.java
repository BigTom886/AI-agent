package com.wc.demo.invoke;

/*

*/
public interface TestApiKey {
    // 密钥不得硬编码在源码里，从环境变量 DASHSCOPE_API_KEY 读取
    String API_KEY = System.getenv("DASHSCOPE_API_KEY");
}
