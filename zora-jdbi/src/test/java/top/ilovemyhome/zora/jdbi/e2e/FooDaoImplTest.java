package top.ilovemyhome.zora.jdbi.e2e;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.jdbi.SearchCriteria;
import top.ilovemyhome.zora.jdbi.e2e.domain.Bar;
import top.ilovemyhome.zora.jdbi.e2e.domain.Foo;
import top.ilovemyhome.zora.jdbi.e2e.impl.BarDaoImpl;
import top.ilovemyhome.zora.jdbi.e2e.impl.FooDaoImpl;
import top.ilovemyhome.zora.jdbi.page.Direction;
import top.ilovemyhome.zora.jdbi.page.Page;
import top.ilovemyhome.zora.jdbi.page.impl.PageRequest;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


public class FooDaoImplTest {

    @Test
    public void testCreateAutoCommit() {
        fooDao.deleteAll();
        barDao.deleteAll();
        fooList.forEach(foo -> {
            fooDao.create(foo);
        });
        barList.forEach(bar -> {
            barDao.create(bar);
        });
        assertThat(fooDao.countAll()).isEqualTo(6);
        assertThat(barDao.countAll()).isEqualTo(3);

        SearchCriteria jackSearch = new SearchCriteria() {
            @Override
            public String whereClause() {
                return " where FIRST_NAME like '%'||:firstName||'%' and LAST_NAME in (<last_names>) ";
            }

            @Override
            public Map<String, Object> normalParams() {
                return Map.of("firstName", "jack");
            }

            @Override
            public Map<String, List<?>> listParam() {
                return Map.of("last_names", List.of("fang", "ma"));
            }
        };
        long countJack = fooDao.count(jackSearch);
        assertThat(countJack).isEqualTo(5);
        List<Foo> fooList = fooDao.find(jackSearch);
        assertThat(fooList.size()).isEqualTo(5);

        List<Long> allIds = fooDao.findIds(jackSearch);
        assertThat(allIds.size()).isEqualTo(5);

        SearchCriteria fooSearchCriteria = new SearchCriteria() {
            @Override
            public String whereClause() {
                return " where FIRST_NAME like :firstName||'%' ";
            }

            @Override
            public Map<String, Object> normalParams() {
                return Map.of("firstName", "jack");
            }
        };
        Page<Foo> firstPage = fooDao.find(
            fooSearchCriteria
            , new PageRequest(1, 2, Direction.DESC, "FIRST_NAME"));

        Page<Foo> secondPage = fooDao.find(
            fooSearchCriteria
            , new PageRequest(2, 2, Direction.DESC, "FIRST_NAME"));

        Page<Foo> thirdPage = fooDao.find(
            fooSearchCriteria
            , new PageRequest(3, 2, Direction.DESC, "FIRST_NAME"));

        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getContent().size()).isEqualTo(2);

        assertThat(secondPage.isFirst()).isFalse();
        assertThat(secondPage.isLast()).isFalse();
        assertThat(secondPage.hasNext()).isTrue();
        assertThat(secondPage.getTotalPages()).isEqualTo(3);
        assertThat(secondPage.getContent().size()).isEqualTo(2);


        assertThat(thirdPage.isFirst()).isFalse();
        assertThat(thirdPage.isLast()).isTrue();
        assertThat(thirdPage.hasNext()).isFalse();
        assertThat(thirdPage.getTotalPages()).isEqualTo(3);
        assertThat(thirdPage.getContent().size()).isEqualTo(1);

    }

    @Test
    public void testUpdate() {
        fooDao.deleteAll();
        barDao.deleteAll();
        fooList.forEach(foo -> {
            fooDao.create(foo);
        });
        barList.forEach(bar -> {
            barDao.create(bar);
        });

        List<Foo> foos = fooDao.find("select * from foo where first_name like '%'||:firstName||'%' " +
                "and country in (<listOfCountry>) order by ID asc "
            , Map.of("firstName", "jack"), Map.of("listOfCountry", List.of("CHINA", "UK")));
        assertThat(foos.size()).isEqualTo(4);

        Foo firstFoo = foos.getFirst();
        Long id = firstFoo.getId();
        Foo newFoo = Foo.builder(firstFoo)
            .withCountry("RUSSIA")
            .build();
        int updateCount = fooDao.update(firstFoo.getId(), newFoo);
        assertThat(updateCount).isEqualTo(1);
        assertThat(fooDao.findAllByIds(List.of(id)).getFirst()).isEqualTo(newFoo);

        updateCount = fooDao.update("update foo set country = 'CHINA' where 1 = :foo and 2 in (<fooList>) "
            , Map.of("foo", 1), Map.of("fooList", List.of(1, 2, 3)));
        assertThat(updateCount).isEqualTo(6);

    }

    @Test
    public void testCreateInTransaction() {
        fooDao.deleteAll();
        jdbi.useTransaction(handle -> {
            fooList.forEach(foo -> {
                fooDao.invokeCreate(foo).withHandle(handle);
            });
        });
    }

    @Test
    public void testCrossDaoInTransactionRollback() {
        fooDao.deleteAll();
        barDao.deleteAll();
        try {
            jdbi.useTransaction(handle -> {
                fooList.forEach(foo -> {
                    fooDao.create(foo);
                });
                List.of("foo", "bar", "baz", "012345678901234567890123456789012345678901234567890123456789").forEach(name -> {
                    barDao.create(Bar.of(name, "bar...bar...", UUID.randomUUID(), YearMonth.of(2025, 1)
                        , Map.of("k1", "v1"), Map.of("k1", 100, "k2", 200)));
                });
            });
        } catch (Throwable t) {
            LOGGER.warn("Error {}.", t.getMessage());
        }
        assertThat(barDao.countAll()).isEqualTo(0);
        assertThat(fooDao.countAll()).isEqualTo(0);
    }


    private static void prepareFooTable() {
        List<String> sqlList = List.of("drop table if exists foo cascade"
            , "drop sequence if exists seq_foo cascade"
            , """
                 create sequence seq_foo increment by 1 minvalue 1
                   no maxvalue start with 1
                """
            , """
                create table foo (
                  ID NUMERIC(22) primary key default nextval('seq_foo'),
                  FIRST_NAME varchar(50) not null,
                  last_name varchar(50) not null,
                  COUNTRY VARCHAR(16) NOT NULL,
                  BIRTH_DATE date ,
                  AGE int ,
                  LAST_MODIFIED timestamp ,
                  OTHERS text
                );
                """
        );

        jdbi.useHandle(handle -> {
            sqlList.forEach(handle::execute);
        });
    }

    private static void prepareBarTable() {
        List<String> sqlList = List.of("drop table if exists bar cascade"
            , "drop sequence if exists seq_bar cascade"
            , """
                 create sequence seq_bar increment by 1 minvalue 1
                   no maxvalue start with 1
                """
            , """
                create table bar (
                  ID NUMERIC(22) primary key default nextval('seq_foo'),
                  NAME varchar(50) not null,
                  OTHERS text,
                  UUID varchar(256),
                  BILLING_MONTH varchar(10) ,
                  ATTRIBUTES varchar(512),
                  INT_ATTRIBUTES varchar(512)
                );
                """);

        jdbi.useHandle(handle -> {
            sqlList.forEach(handle::execute);
        });
    }

    private final List<Foo> fooList = List.of(
        Foo.builder()
            .withFirstName("jack")
            .withLastName("fang")
            .withCountry("CHINA")
            .withBirthDate(LocalDate.of(1999, 1, 3))
            .withAge(19)
            .withLastModified(LocalDateTime.now())
            .withOthers("Are you OK")
            .build()
        , Foo.builder()
            .withFirstName("bill")
            .withLastName("gates")
            .withCountry("USA")
            .withBirthDate(LocalDate.of(2000, 12, 3))
            .withAge(55)
            .withLastModified(LocalDateTime.now())
            .withOthers("Are you OK")
            .build()
        , Foo.builder()
            .withFirstName("jack1")
            .withLastName("ma")
            .withCountry("CHINA")
            .withBirthDate(LocalDate.of(1988, 8, 8))
            .withAge(66)
            .withLastModified(LocalDateTime.now())
            .withOthers("Are you OK")
            .build()
        , Foo.builder()
            .withFirstName("jack2")
            .withLastName("ma")
            .withCountry("CHINA")
            .withBirthDate(LocalDate.of(1988, 1, 8))
            .withAge(66)
            .withLastModified(LocalDateTime.now())
            .withOthers("Are you OK")
            .build()
        , Foo.builder()
            .withFirstName("jack3")
            .withLastName("ma")
            .withCountry("UK")
            .withBirthDate(LocalDate.of(1980, 9, 8))
            .withAge(60)
            .withLastModified(LocalDateTime.now())
            .withOthers("Are you OK")
            .build()
        , Foo.builder()
            .withFirstName("jack4")
            .withLastName("ma")
            .withCountry("USA")
            .withBirthDate(LocalDate.of(1980, 9, 8))
            .withAge(60)
            .withLastModified(LocalDateTime.now())
            .withOthers("Are you OK")
            .build()
    );

    private final Foo badFoo = Foo.builder()
        .withFirstName("tooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo_long_name")
        .withLastName("foo")
        .build();

    private final List<Bar> barList = List.of(
        Bar.of("jack fang", "jack is a fool.", UUID.randomUUID()
            , YearMonth.of(2024, 12), Map.of("kkk", "vvv"), Map.of("kkk", 100, "kkk2", 200, "kkk3", 300))
        , Bar.of("gates bill", "gates is a rich guy.")
        , Bar.of("jack ma", "jack ma is a rich guy as well")
    );
    private final Bar badBar = Bar.of("bar_name_tooooooooooooooooooooooooooooooooooooooooooooooooooooo_long", "bar bar");


    static Jdbi jdbi;
    static FooDao fooDao;
    static BarDao barDao;


    @BeforeAll
    public static void initAll() throws Exception {
        try {
            pg = EmbeddedPostgres.builder()
                .setLocaleConfig("locale", "en_US")
                .setLocaleConfig("encoding", "UTF-8")
                .start();
            DataSource dataSource = pg.getPostgresDatabase();
            jdbi = Jdbi.create(dataSource);
            prepareFooTable();
            prepareBarTable();
            fooDao = new FooDaoImpl(jdbi);
            barDao = new BarDaoImpl(jdbi);
        }catch (Exception e) {
            LOGGER.error("Error {}.", e.getMessage());
            throw e;
        }
    }

    @AfterAll
    public static void closeAll() throws Exception {
        if (Objects.nonNull(pg)) {
            pg.close();
        }
    }

    private static EmbeddedPostgres pg;

    private static final Logger LOGGER = LoggerFactory.getLogger(FooDaoImplTest.class);

}
