package top.ilovemyhome.zora.json.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.YearMonthDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.YearMonthSerializer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static top.ilovemyhome.zora.json.Constants.*;

public final class JacksonUtil {

    public static final ObjectMapper MAPPER;

    public static String toJson(Object obj) {
        if (Objects.isNull(obj)){
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        }catch (JsonProcessingException e){
            LOGGER.error("Failed serializing object to json.", e);
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (StringUtils.isEmpty(json)){
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        }catch (Throwable t){
            LOGGER.error("Failed deserializing object to json.", t);
            throw new RuntimeException(t);
        }
    }


    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (StringUtils.isEmpty(json)){
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        }catch (Throwable t){
            LOGGER.error("Failed deserializing object to json.", t);
            throw new RuntimeException(t);
        }
    }

    static {
        MAPPER = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addDeserializer(LocalDate.class,
            new LocalDateDeserializer(DateTimeFormatter.ofPattern(JSON_DATE_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
            new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(JSON_DATETIME_FORMAT)));
        javaTimeModule.addDeserializer(YearMonth.class,
            new YearMonthDeserializer(DateTimeFormatter.ofPattern(JSON_MONTH_FORMAT)));
        javaTimeModule.addDeserializer(LocalTime.class,
            new LocalTimeDeserializer(DateTimeFormatter.ofPattern(JSON_TIME_FORMAT)));

        javaTimeModule.addSerializer(LocalDate.class,
            new LocalDateSerializer(DateTimeFormatter.ofPattern(JSON_DATE_FORMAT)));
        javaTimeModule.addSerializer(LocalDateTime.class,
            new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(JSON_DATETIME_FORMAT)));
        javaTimeModule.addSerializer(YearMonth.class,
            new YearMonthSerializer(DateTimeFormatter.ofPattern(JSON_MONTH_FORMAT)));
        javaTimeModule.addSerializer(LocalTime.class,
            new LocalTimeSerializer(DateTimeFormatter.ofPattern(JSON_TIME_FORMAT)));



        MAPPER.registerModule(javaTimeModule);
        MAPPER.configure(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS, false);
        MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }



    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonUtil.class);
}
