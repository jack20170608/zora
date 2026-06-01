# requestNo（reqNo）的生成策略

我直接用**交易系统生产标准**给你讲透，没有任何模糊点。

---

# 一、结论先说
**requestNo = 请求唯一标识 / 幂等号**
**它一定是【请求发起方】生成，不是服务端生成！**

## 谁生成？
1. **前端/客户端/APP/小程序 → 生成**
2. **第三方调用方（如渠道、商户）→ 生成**
3. **内部上游系统（如订单系统调用支付）→ 生成**

## 服务端（你的交易系统）做什么？
**只负责校验、防重、持久化，不负责生成！**

---

# 二、为什么必须由【调用方】生成？
因为：
### **如果服务端生成，就无法防重！**

举个例子：
- 前端点击提交 → 网络超时
- 前端重试 → 发第二次
- 如果服务端每次生成新 reqNo → **两次都会执行 → 重复下单/重复扣款**

只有**调用方自带唯一编号**，重试时带上同一个编号，服务端才能识别：
**哦，这是同一个请求，我不能再处理！**

---

# 三、requestNo 生成规则（生产标准）
## 格式必须满足 3 点：
1. **全局唯一**
2. **不变**（重试必须一样）
3. **可读可查**

## 最常用生成方案（任选其一即可）
### 方案1：UUID（最简单）
```
requestNo = UUID.randomUUID().toString()
```

### 方案2：时间戳 + 随机数（推荐）
```
requestNo = "REQ" + System.currentTimeMillis() + RandomStringUtils.random(6)
```
例：
```
REQ20250405120000123456
```

### 方案3：分布式ID（大厂标准）
- 雪花算法
- Leaf / UidGenerator
```
requestNo = "PAY" + idGenerator.nextId()
```

---

# 四、一个请求的完整生命周期（你马上就能理解）
## 1. 调用方（前端/渠道）生成
```
requestNo = PAY20250405120000001
```

## 2. 调用方发起请求（带着 requestNo）
```
{
  "requestNo": "PAY20250405120000001",
  "businessType": "transfer",
  "userId": 1001,
  "amount": 100
}
```

## 3. 服务端接收
```
key = idempotent:transfer:PAY20250405120000001
```

## 4. 查 RocksDB
- 不存在 → 占坑 → 执行业务
- 已存在 → 返回“重复请求”

---

# 五、最关键的一句话（必须记住）
### **requestNo 是请求的身份证，谁发起请求，谁带身份证。**

服务端只做：
**验身份证 → 防重复 → 保证幂等**

---

# 六、Java 调用方生成 requestNo 示例（前端/渠道都这么写）
```java
// 调用方生成唯一请求号
String requestNo = "REQ" + System.currentTimeMillis() + 
                    ThreadLocalRandom.current().nextInt(100000, 999999);

// 带着 requestNo 发起请求
httpPost("/api/pay", new PayRequest(requestNo, userId, amount));
```

---

# 七、我再给你一个行业标准定义（你可以直接写进文档）
- **requestNo**：**请求幂等编号**
- **生成方**：请求发起方
- **作用**：确保请求唯一、防止重复提交、支持安全重试
- **存储**：服务端使用 RocksDB 持久化防重

---

如果你愿意，我可以直接给你：
**一套「调用方生成 requestNo + 服务端 RocksDB 防重」的完整双端代码（可直接上线）**
