# 生成 RSA 密钥对

## 1. 生成私钥（无密码保护）
```shell
# 生成 2048 位 RSA 私钥（推荐生产环境用 4096 位，安全性更高）
openssl genrsa -out private_key.pem 2048

# 4096 位版本（更安全，性能略低）
openssl genrsa -out private_key_4096.pem 4096
```

## 2. 从私钥提取公钥
```shell
openssl rsa -in private_key.pem -pubout -out public_key.pem
```

## 3. 验证密钥（可选）
```shell
# 查看私钥信息
openssl rsa -in private_key.pem -text -noout

# 查看公钥信息
openssl rsa -in public_key.pem -pubin -text -noout
```
