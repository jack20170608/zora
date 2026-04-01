package top.ilovemyhome.zora.httpclient.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.muserver.MuRequest;
import io.muserver.UploadedFile;
import io.muserver.rest.Description;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Path("/api/v1/order")
public class OrderHandler {

    @GET
    @Path("/getall")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAll() {
        try {
            return Response.ok(MAPPER.writeValueAsString(ORDER_DB.values())).build();
        } catch (JsonProcessingException e) {
            LOGGER.warn("Json process error", e);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/reset")
    public Response resetData() {
        reset();
        return Response.ok("success").build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id : [0-9]+}")
    public Response get(@PathParam("id") long id) {
        Order order = Optional.ofNullable(ORDER_DB.get(id)).orElseThrow(() -> new NotFoundException("Order with id " + id + " not found"));
        try {
            return Response.ok(MAPPER.writeValueAsString(order)).build();
        } catch (JsonProcessingException e) {
            LOGGER.warn("Json process error", e);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/query")
    @Produces(MediaType.APPLICATION_JSON)
    public Response query(@QueryParam("id") Long id, @QueryParam("counterparty") String counterparty) {
        Optional<Order> order = Optional.ofNullable(ORDER_DB.get(id));
        if (order.isPresent() && order.get().getCounterparty().equals(counterparty)) {
            return Response.ok(order.get()).build();
        }
        throw new NotFoundException("Order with id " + id + " not found");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(Order order) {
        order.setId(ID_GENERATOR.incrementAndGet());
        ORDER_DB.put(order.getId(), order);
        return Response.ok(order).build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id : [0-9]+}")
    public Response delete(@PathParam("id") long id) {
        Order order = Optional.ofNullable(ORDER_DB.get(id)).orElseThrow(() -> new NotFoundException("Order with id " + id + " not found"));
        ORDER_DB.remove(id);
        return Response.ok(order).build();
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/put")
    public Response put(@QueryParam("id") Long id, Order order) {
        Order old = Optional.ofNullable(ORDER_DB.get(id)).orElseThrow(() -> new NotFoundException("Order with id " + id + " not found"));
        order.setId(old.getId());
        ORDER_DB.put(id, order);
        return Response.ok(order).build();
    }

    @GET
    @Path("/internalError")
    @Produces(MediaType.TEXT_PLAIN)
    public Response internalError(@QueryParam("number") int number) {
        int i = SEQ_GENERATOR.getAndIncrement();
        if (i % 20 != number % 20) {
            throw new InternalServerErrorException("Mocked Internal server error");
        }
        return Response.ok("success").build();
    }

    @GET
    @Path("/requestError")
    @Produces(MediaType.TEXT_PLAIN)
    public Response requestError(@QueryParam("number") int number) {
        int i = SEQ_GENERATOR.getAndIncrement();
        if (i % 20 != number % 20) {
            throw new BadRequestException("Mocked bad request error");
        }
        return Response.ok("success").build();
    }

    @POST
    @Path("/fileUpload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    @Description("Test file upload")
    public Response fileUpload(@FormParam("files") List<UploadedFile> uploadedFiles) {
        uploadedFiles.forEach(this::saveToDisk);
        return Response.ok().build();
    }

    @POST
    @Path("/fileUpload2")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    @Description("Test file upload2")
    public Response fileUpload2(@Context MuRequest request) {
        try {
            List<UploadedFile> uploadedFiles = request.uploadedFiles("files");
            uploadedFiles.forEach(this::saveToDisk);
            return Response.ok().build();
        }catch (IOException e) {
            LOGGER.warn("IO exception", e);
            return Response.serverError().build();
        }
    }

    private void saveToDisk(UploadedFile uploadedFile) {
        try {
            String targetFileName = uploadedFile.asFile().getName();
            uploadedFile.saveTo(Paths.get(this.tempDir, uploadedFile.filename()).toFile());
            LOGGER.info(uploadedFile.filename() + " (" + uploadedFile.size() + " bytes) of type " + uploadedFile.contentType()
                + " saved to [{}].", targetFileName);
        } catch (Throwable t) {
            LOGGER.warn("File save to disk failure.", t);
            throw new InternalServerErrorException("Internal Server error", t);
        }
    }

    private final String tempDir = System.getProperty("java.io.tmpdir");

    private static ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicLong ID_GENERATOR = new AtomicLong();
    private static final AtomicInteger SEQ_GENERATOR = new AtomicInteger();
    private static final Map<Long, Order> ORDER_DB = new HashMap<>();

    static {
        reset();
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    private static void reset() {
        ORDER_DB.clear();
        ID_GENERATOR.set(0);
        SEQ_GENERATOR.set(0);
        ORDER_DB.put(ID_GENERATOR.incrementAndGet(), new Order(ID_GENERATOR.get(), "CCB", new BigDecimal("100.00")));
        ORDER_DB.put(ID_GENERATOR.incrementAndGet(), new Order(ID_GENERATOR.get(), "CB", new BigDecimal("-100.00")));
        ORDER_DB.put(ID_GENERATOR.incrementAndGet(), new Order(ID_GENERATOR.get(), "ICBC", new BigDecimal("-100.00")));
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderHandler.class);
}
