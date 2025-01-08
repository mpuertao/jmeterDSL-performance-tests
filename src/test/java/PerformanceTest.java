import org.apache.http.entity.ContentType;
import org.apache.jmeter.protocol.http.util.HTTPConstants;
import org.junit.jupiter.api.Test;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static us.abstracta.jmeter.javadsl.JmeterDsl.*;


public class PerformanceTest  {





    @Test
    public void testPerformance() throws IOException {

        String url = "https://restful-booker.herokuapp.com";
        String path = "/booking";

        InputStream inputStream = PerformanceTest.class.getResourceAsStream("utils/request.json");
        String bodyRequest = new String(inputStream.readAllBytes());


        TestPlanStats stats = testPlan(
                threadGroup()
                        .rampToAndHold(5, Duration.ofSeconds(5), Duration.ofSeconds(10))
                        .rampToAndHold(15, Duration.ofSeconds(10), Duration.ofSeconds(30))
                        .rampTo(20, Duration.ofSeconds(10))
                        .rampToAndHold(10, Duration.ofSeconds(10), Duration.ofSeconds(30))
                        .rampTo(0, Duration.ofSeconds(10))
                        .children(
                            httpSampler("AUTH", url + "/auth")
                                    .contentType(ContentType.APPLICATION_JSON)
                                    .header("Accept", "application/json")
                                    .method(HTTPConstants.POST)
                                    .body("{\"username\" : \"admin\",\"password\" : \"password123\"}")
                                    .children(
                                            jsr223PostProcessor(
                                                    """
                                                            vars.put("authToken", new groovy.json.JsonSlurper()
                                                                                            .parse(prev.getResponseData())
                                                                                            .token.toString());
                                                          """
                                                            )
                                                    ),
                            httpSampler("GET", url + path),
                            httpSampler("POST", url + path)
                                    .method(HTTPConstants.POST)
                                    .contentType(ContentType.APPLICATION_JSON)
                                    .header("Accept", "application/json")
                                    .body(bodyRequest)
                                    .children(
                                            jsr223PostProcessor(
                                                    """
                                                            vars.put("bookingid", new groovy.json.JsonSlurper()
                                                                                            .parse(prev.getResponseData())
                                                                                            .bookingid.toString());
                                                          """
                                            )
                                    ),
                            httpSampler("PUT", url + path + "/${bookingid}")
                                    .contentType(ContentType.APPLICATION_JSON)
                                    .header("Accept", "application/json")
                                    .header("Cookie", "token=${authToken}")
                                    .method(HTTPConstants.PUT)
                                    .body(bodyRequest),
                            httpSampler("DELETE", url + path + "/${bookingid}")
                                    .contentType(ContentType.APPLICATION_JSON)
                                    .header("Accept", "application/json")
                                    .header("Cookie", "token=${authToken}")
                                    .method(HTTPConstants.DELETE)
                ),
                        //autoStop().when(errors().total().greaterThan(0L)),

                        jtlWriter("target/jtls"),
                        htmlReporter("target"),
                        influxDbListener("http://localhost:8086/api/v2/write?bucket=jmeterdsl&org=T-EVOLVERS")
                                .application("JMETER DSL")
                                .token("123456789")
                                .title("JMETER DSL")
                ).run();
        assertThat(stats.overall().sampleTimePercentile99()).isLessThan(Duration.ofSeconds(20));
        //assertThat(stats.overall().errorsCount()).isEqualTo(0);
    }
}
