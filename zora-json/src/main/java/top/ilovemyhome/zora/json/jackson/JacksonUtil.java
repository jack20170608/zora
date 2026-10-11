package top.ilovemyhome.zora.json.jackson;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.YearMonthDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.YearMonthSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

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
        }catch (JacksonException e){
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
        }catch (JacksonException e){
            LOGGER.error("Failed deserializing object from json.", e);
            throw new RuntimeException(e);
        }
    }


    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (StringUtils.isEmpty(json)){
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        }catch (JacksonException e){
            LOGGER.error("Failed deserializing object from json.", e);
            throw new RuntimeException(e);
        }
    }

    static {
        SimpleModule javaTimeModule = new SimpleModule();
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



        MAPPER = JsonMapper.builder()
            .addModule(javaTimeModule)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }



    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonUtil.class);
}
