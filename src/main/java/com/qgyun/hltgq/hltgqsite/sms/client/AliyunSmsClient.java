package com.qgyun.hltgq.hltgqsite.sms.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.sms.SmsProperties;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 阿里云短信客户端（Dysmsapi 2017-05-25，RPC 风格，零 SDK 依赖）。
 * <p>签名算法：参数按 key 字典序 → RFC3986 编码拼 canonicalizedQueryString →
 * StringToSign = "POST&%2F&" + percentEncode(canonicalized) → HMAC-SHA1(secret + "&") → Base64。
 * 同一算法已用本项目 AK 在真实环境验证（发送测试短信成功），实现与阿里云文档一致。
 * <p>不引入官方 SDK 的原因：构建私服拉不到 dysmsapi20170525 新 SDK（旧 SDK 传递 fastjson 1.1.x
 * 在本仓库同样缺失），手写签名只依赖 JDK，避免多出 9 个传递包与构建失败风险。
 * <p>线程安全：RestTemplate 无状态；参数局部构造，可被定时任务与回调接口并发调用。
 */
@Component
public class AliyunSmsClient {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsClient.class);

    private static final String API_VERSION = "2017-05-25";
    private static final String SIGNATURE_METHOD = "HMAC-SHA1";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final SmsProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AliyunSmsClient(SmsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 发送单条短信。
     *
     * @param phone             接收号码（E.164 不带 +，如 13800000000）
     * @param templateCode      模板编码（SMS_xxx）
     * @param templateParamJson 模板变量 JSON（发送服务统一序列化，发送与落库共用一份；值不可为空串）
     * @return 发送结果；网络异常返回 code=NETWORK_ERROR（可重试），业务错误不重试
     */
    public SmsSendResult send(String phone, String templateCode, String templateParamJson) {
        if (!properties.isClientConfigured()) {
            return SmsSendResult.fail("NOT_CONFIGURED", "短信 AK/签名未配置");
        }

        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", properties.getAccessKeyId());
        params.put("Action", "SendSms");
        params.put("Format", "JSON");
        params.put("PhoneNumbers", phone);
        params.put("RegionId", properties.getRegionId());
        params.put("SignName", properties.getSignName());
        params.put("SignatureMethod", SIGNATURE_METHOD);
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("TemplateCode", templateCode);
        params.put("TemplateParam", templateParamJson);
        params.put("Timestamp", ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP_FORMAT));
        params.put("Version", API_VERSION);

        try {
            String canonicalized = canonicalizedQueryString(params);
            String signature = sign(canonicalized, properties.getAccessKeySecret());
            String body = canonicalized + "&Signature=" + percentEncode(signature);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    properties.getEndpoint() + "/", new HttpEntity<>(body, headers), String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            // 超时/连接失败（RestClientException、SocketException 均落入此处）
            log.warn("阿里云短信调用异常 phone={} template={} err={}", maskPhone(phone), templateCode, e.toString());
            return SmsSendResult.fail("NETWORK_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 解析阿里云响应：Code=OK 视为受理成功 */
    private SmsSendResult parseResponse(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isEmpty()) {
            return SmsSendResult.fail("EMPTY_RESPONSE", "阿里云返回空响应");
        }
        JsonNode root = objectMapper.readTree(responseBody);
        String code = root.path("Code").asText("");
        String message = root.path("Message").asText("");
        if ("OK".equals(code)) {
            return SmsSendResult.ok(root.path("BizId").asText(""), root.path("RequestId").asText(""));
        }
        return SmsSendResult.fail(code.isEmpty() ? "UNKNOWN" : code, message);
    }

    /** 参数按 key 字典序拼 a=1&b=2（key/value 均 RFC3986 编码） */
    private static String canonicalizedQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(percentEncode(entry.getKey())).append('=').append(percentEncode(entry.getValue()));
        }
        return sb.toString();
    }

    /** HMAC-SHA1 签名，密钥为 secret + "&" */
    private static String sign(String canonicalized, String accessKeySecret) throws Exception {
        String stringToSign = "POST" + "&" + percentEncode("/") + "&" + percentEncode(canonicalized);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] digest = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    /** RFC3986 编码（阿里云签名规则：空格 %20、* %2A、~ 保留） */
    private static String percentEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            throw new IllegalStateException("URL 编码失败", e);
        }
    }

    /** 手机号脱敏（日志用） */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
