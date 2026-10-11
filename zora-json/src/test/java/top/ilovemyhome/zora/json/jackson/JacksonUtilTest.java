package top.ilovemyhome.zora.json.jackson;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.common.io.ResourceUtil;
import top.ilovemyhome.zora.json.domain.Address;
import top.ilovemyhome.zora.json.domain.People;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class JacksonUtilTest {

    @BeforeAll
    public static void initData() {
        String jsonStr = ResourceUtil.getClasspathResourceAsString("data/all-people.json");
        DATAS.addAll(Objects.requireNonNull(JacksonUtil.fromJson(jsonStr, new TypeReference<>() {})));
        assertThat(DATAS.size()).isEqualTo(2);
        logger.info("test logger!");
    }

    @Test
    public void testJsonSerialization() {
        Set<People> peopleSet = JacksonUtil.fromJson(JacksonUtil.toJson(DATAS), new TypeReference<>() {});
        assertThat(peopleSet).isEqualTo(DATAS);
    }

    @Test
    public void testJavaTimeFormats() {
        assertThat(JacksonUtil.toJson(LocalDate.of(2024, 2, 3))).isEqualTo("\"2024-02-03\"");
        assertThat(JacksonUtil.toJson(LocalDateTime.of(2024, 2, 3, 4, 5, 6)))
            .isEqualTo("\"2024-02-03 04:05:06.000\"");
        assertThat(JacksonUtil.toJson(YearMonth.of(2024, 2))).isEqualTo("\"202402\"");
        assertThat(JacksonUtil.toJson(LocalTime.of(4, 5, 6))).isEqualTo("\"04:05:06.000\"");

        assertThat(JacksonUtil.fromJson("\"2024-02-03\"", LocalDate.class))
            .isEqualTo(LocalDate.of(2024, 2, 3));
        assertThat(JacksonUtil.fromJson("\"2024-02-03 04:05:06.000\"", LocalDateTime.class))
            .isEqualTo(LocalDateTime.of(2024, 2, 3, 4, 5, 6));
        assertThat(JacksonUtil.fromJson("\"202402\"", YearMonth.class))
            .isEqualTo(YearMonth.of(2024, 2));
        assertThat(JacksonUtil.fromJson("\"04:05:06.000\"", LocalTime.class))
            .isEqualTo(LocalTime.of(4, 5, 6));
    }

    @Test
    public void testUnknownPropertiesAreRejected() {
        assertThatThrownBy(() -> JacksonUtil.MAPPER.readValue("{\"unexpected\":1}", Address.class))
            .isInstanceOf(UnrecognizedPropertyException.class);
    }

    private static final Set<People> DATAS = new HashSet<>();

    private static final Logger logger = LoggerFactory.getLogger(JacksonUtilTest.class);

}



