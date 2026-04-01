# zora-common

zora-common 是 zora 项目的核心通用工具模块，提供了日常Java开发中常用的工具类和基础抽象。

## 模块结构

```
top.ilovemyhome.zora.common/
├── codec/          # 编解码工具
│   ├── DigestUtils  - 摘要计算工具（MD5, SHA 等）
│   └── Hex         - Hex十六进制编码解码
├── io/             # IO/资源处理
│   ├── FileHelper  - 文件操作工具
│   └── ResourceUtil - 资源加载工具
├── lang/           # 语言基础扩展
│   ├── ArrayUtils  - 数组工具
│   ├── ByteHelper  - 字节处理工具
│   ├── ClassUtils  - 类操作工具
│   ├── CollectionUtil - 集合工具
│   ├── CodecException - 编解码异常
│   ├── CodecSupport - 编解码支持基类
│   ├── MapContext  - 基于Map的上下文抽象
│   ├── MapUtil    - Map工具
│   ├── StringConvertUtils - 字符串转换工具
│   └── exceptions - 特定异常类
├── lifecycle/      # 生命周期接口
│   ├── Destroyable - 销毁接口
│   ├── Initializable - 初始化接口
│   └── LifecycleUtils - 生命周期工具
├── number/         # 数字处理
│   ├── DecimalUtils - 小数工具
│   └── IdGenerator - ID生成器
├── date/           # 日期时间工具
│   └── LocalDateUtils - java.time.LocalDate/LocalDateTime 工具
├── serialize/      # 序列化
│   └── JdkSerializeTool - JDK序列化工具
├── system/         # 系统信息
│   ├── NetUtil     - 网络工具
│   ├── OSUtil      - OS信息获取
│   └── SystemCommandChecker - 系统命令检查
├── text/           # 文本处理
│   ├── FancySeparatorUtils - 分隔线工具
│   └── StrUtils   - 字符串工具
└── validate/       # 验证
    └── ValidateHelper - 参数验证工具
```

## 依赖

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖外部库：
- `slf4j-api` - 日志门面
- `commons-lang3` - Apache Commons Lang
- `guava` - Google Guava
- `junit-jupiter-api` + `assertj-core` - 测试（仅测试范围）

## 使用说明

每个工具类都是静态方法不可实例化，直接通过类名调用即可。

示例：

```java
// 日期时间处理
String today = LocalDateUtils.getLocalDateStr();

// 十六进制编码
byte[] decoded = Hex.decode(hexString);
String encoded = Hex.encodeToString(bytes);

// 集合操作
boolean isEmpty = CollectionUtil.isEmpty(list);
```

## License

Copyright © 2025 zora
