package org.vanvalenlab;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonSyntaxException;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class KioskJobTest {

    private KioskJob kioskJob;
    private MockWebServer server;
    private HttpUrl baseUrl;
    private String jobType = "test";

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = server.url("/api");
        kioskJob = new KioskJob(baseUrl.toString(), jobType);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testHasFinalStatus() throws IOException {
        // format string for easily creating responses.
        String responseFmtStr = "{\"status\": \"%s\"}";

        // new job should have null (false) final status
        assertEquals(null, kioskJob.getStatus());
        assertEquals(false, kioskJob.hasFinalStatus());

        // update status to intermediate status
        String nextStatus = "testing";
        server.enqueue(new MockResponse().setBody(String.format(responseFmtStr, nextStatus)));
        kioskJob.updateStatus();
        assertEquals(nextStatus, kioskJob.getStatus());
        assertEquals(false, kioskJob.hasFinalStatus());

        // update status to failed status
        nextStatus = Constants.FAILED_STATUS;
        server.enqueue(new MockResponse().setBody(String.format(responseFmtStr, nextStatus)));
        kioskJob.updateStatus();
        assertEquals(nextStatus, kioskJob.getStatus());
        assertEquals(true, kioskJob.hasFinalStatus());

        // update status to success status
        nextStatus = Constants.SUCCESS_STATUS;
        server.enqueue(new MockResponse().setBody(String.format(responseFmtStr, nextStatus)));
        kioskJob.updateStatus();
        assertEquals(nextStatus, kioskJob.getStatus());
        assertEquals(true, kioskJob.hasFinalStatus());
    }

    @Test
    public void testWaitForFinalStatus() throws IOException {
        int updateInterval = 0; // don't sleep in the tests!
        String expectedFinalStatus = Constants.SUCCESS_STATUS;
        // format string for easily creating responses.
        String responseFmtStr = "{\"status\": \"%s\"}";

        // 4 status updates, null -> testing -> testing -> done
        server.enqueue(new MockResponse().setBody("{}"));
        server.enqueue(new MockResponse().setBody(
                String.format(responseFmtStr, "testing")));
        server.enqueue(new MockResponse().setBody(
                String.format(responseFmtStr, "testing")));
        server.enqueue(new MockResponse().setBody(String.format(
                responseFmtStr, expectedFinalStatus)));

        String finalStatus = kioskJob.waitForFinalStatus(updateInterval);
        assertEquals(expectedFinalStatus, finalStatus);
    }

    @Test
    public void testCreate() throws IOException {
        File temporaryFile = folder.newFile();
        String filePath = temporaryFile.getAbsolutePath();

        // successful responses
        String expectedUploadPath = "uploadedFilePath.jpg";
        String expectedURL = "http://test.com/uploadedFilePath.jpg";
        String expectedUploadResponse = String.format(
                "{\"uploadedName\": \"%s\", \"imageURL\": \"%s\"}",
                expectedUploadPath, expectedURL);
        String expectedHash = "newJobHash";
        String expectedCreateResponse = String.format("{\"hash\": \"%s\"}", expectedHash);
        server.enqueue(new MockResponse().setBody(expectedUploadResponse));
        server.enqueue(new MockResponse().setBody(expectedCreateResponse));
        kioskJob.create(filePath);
        assertEquals(expectedHash, kioskJob.getJobHash());
        assertEquals(jobType, kioskJob.getJobType());

        // upload failure: valid but empty JSON
        server.enqueue(new MockResponse().setBody("{}"));
        assertThrows(IOException.class, () ->
                kioskJob.create(filePath)
        );

        // upload failure: local file does not exist throws error.
        String invalidFilePath = String.format(
                "%s%s", folder.getRoot().getAbsolutePath(), "invalid.jpg");
        assertThrows(IOException.class, () ->
                kioskJob.create(invalidFilePath)
        );

        // upload failure: error response
        server.enqueue(new MockResponse().setResponseCode(500).setBody("failed"));
        assertThrows(IOException.class, () ->
                kioskJob.create(filePath)
        );

        // upload failure: invalid JSON but successful response
        server.enqueue(new MockResponse().setBody("failed"));
        assertThrows(JsonSyntaxException.class, () ->
                kioskJob.create(filePath)
        );

        // create failure: valid but empty JSON
        server.enqueue(new MockResponse().setBody(expectedUploadResponse));
        server.enqueue(new MockResponse().setBody("{}"));
        kioskJob.create(filePath);
        assertEquals(null, kioskJob.getJobHash());

        // create failure: error response
        server.enqueue(new MockResponse().setBody(expectedUploadResponse));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("failed"));
        assertThrows(IOException.class, () ->
                kioskJob.create(filePath)
        );

        // create failure: invalid JSON but successful response
        server.enqueue(new MockResponse().setBody(expectedUploadResponse));
        server.enqueue(new MockResponse().setBody("failed"));
        assertThrows(JsonSyntaxException.class, () ->
                kioskJob.create(filePath)
        );


    }

    @Test
    public void testExpire() throws IOException {
        int expireTime = 1;
        // successful response
        int expectedValue = 99;
        assertEquals(false, kioskJob.isExpired());
        String expectedResponse = String.format("{\"value\": \"%s\"}", expectedValue);
        server.enqueue(new MockResponse().setBody(expectedResponse));
        kioskJob.expire(expireTime);
        assertEquals(expectedValue, expectedValue);
        assertEquals(true, kioskJob.isExpired());

        // 0 response throws error, hash not found.
        server.enqueue(new MockResponse().setBody("{\"value\": 0}"));
        assertThrows(IOException.class, () ->
                kioskJob.expire(expireTime)
        );

        // valid but empty JSON
        server.enqueue(new MockResponse().setBody("{}"));
        assertThrows(IOException.class, () ->
                kioskJob.expire(expireTime)
        );

        // error response
        server.enqueue(new MockResponse().setResponseCode(500).setBody("failed"));
        assertThrows(IOException.class, () ->
                kioskJob.expire(expireTime)
        );

        // invalid JSON but successful response
        server.enqueue(new MockResponse().setBody("failed"));
        assertThrows(JsonSyntaxException.class, () ->
                kioskJob.expire(expireTime)
        );
    }

    @Test
    public void testUpdateStatus() throws IOException {
        // successful response
        String expectedValue = "success!";
        String expectedResponse = String.format("{\"status\": \"%s\"}", expectedValue);
        server.enqueue(new MockResponse().setBody(expectedResponse));
        kioskJob.updateStatus();
        assertEquals(expectedValue, kioskJob.getStatus());

        // valid but empty JSON
        server.enqueue(new MockResponse().setBody("{}"));
        kioskJob.updateStatus();
        assertEquals(null, kioskJob.getStatus());

        // error response
        server.enqueue(new MockResponse().setResponseCode(500).setBody("failed"));
        assertThrows(IOException.class, () ->
                kioskJob.updateStatus()
        );

        // invalid JSON but successful response
        server.enqueue(new MockResponse().setBody("failed"));
        assertThrows(JsonSyntaxException.class, () ->
                kioskJob.updateStatus()
        );
    }

    @Test
    public void testGetOutputPath() throws IOException {
        // successful response
        String expectedValue = "success!";
        String expectedResponse = String.format("{\"value\": \"%s\"}", expectedValue);
        server.enqueue(new MockResponse().setBody(expectedResponse));
        String response = kioskJob.getOutputPath();
        assertEquals(expectedValue, response);

        // valid but empty JSON
        server.enqueue(new MockResponse().setBody("{}"));
        response = kioskJob.getOutputPath();
        assertEquals(null, response);

        // error response
        server.enqueue(new MockResponse().setResponseCode(500).setBody("failed"));
        assertThrows(IOException.class, () ->
                kioskJob.getOutputPath()
        );

        // invalid JSON but successful response
        server.enqueue(new MockResponse().setBody("failed"));
        assertThrows(JsonSyntaxException.class, () ->
                kioskJob.getOutputPath()
        );
    }

    @Test
    public void testGetErrorReason() throws IOException {
        // successful response
        String expectedValue = "success!";
        String expectedResponse = String.format("{\"value\": \"%s\"}", expectedValue);
        server.enqueue(new MockResponse().setBody(expectedResponse));
        String response = kioskJob.getErrorReason();
        assertEquals(expectedValue, response);

        // valid but empty JSON
        server.enqueue(new MockResponse().setBody("{}"));
        response = kioskJob.getErrorReason();
        assertEquals(null, response);

        // error response
        server.enqueue(new MockResponse().setResponseCode(500).setBody("failed"));
        assertThrows(IOException.class, () ->
                kioskJob.getErrorReason()
        );

        // invalid JSON but successful response
        server.enqueue(new MockResponse().setBody("failed"));
        assertThrows(JsonSyntaxException.class, () ->
                kioskJob.getErrorReason()
        );
    }

//    @Test
//    public void assureThatDoSomethingReturnsLowValueInts() {
//        int returned = _testObject.doSomething("", 0);
//        assertThat(returned, Matchers.is(0));
//        returned = _testObject.doSomething("", 9);
//        assertThat(returned, Matchers.is(9));
//        returned = _testObject.doSomething("", Integer.MIN_VALUE);
//        assertThat(returned, Matchers.is(Integer.MIN_VALUE));
//    }

//    @Test
//    public void assureThatDoSomethingReturnsParsedStringForHighParam2Values() {
//        int returned = _testObject.doSomething("1234", 10);
//        assertThat(returned, Matchers.is(1234));
//        returned = _testObject.doSomething("4567", Integer.MAX_VALUE);
//        assertThat(returned, Matchers.is(4567));
//    }

//    @Test(expected=NumberFormatException.class)
//    public void assureThatDoSomethingThrowsExceptionForNonParseableString() {
//        _testObject.doSomething("+-*~", 10);
//    }
}
