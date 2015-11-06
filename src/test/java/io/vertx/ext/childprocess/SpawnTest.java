package io.vertx.ext.childprocess;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public class SpawnTest {

  Vertx vertx;

  @Before
  public void before() {
    vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testExitCode(TestContext testContext) {
    Async async = testContext.async();
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      ChildProcess.spawn(vertx, Arrays.asList("/usr/bin/java", "-cp", "target/test-classes", "ExitCode"), process -> {
        process.exitHandler(code -> {
          testContext.assertEquals(context, Vertx.currentContext());
          testContext.assertEquals(25, code);
          async.complete();
        });
      });
    });
  }

  @Test
  public void testStdin(TestContext context) {
    Async async = context.async();
    AtomicInteger status = new AtomicInteger();
    ChildProcess.spawn(vertx, Arrays.asList("read"), process -> {
      ProcessWriteStream stdin = process.stdin();
      stdin.write(Buffer.buffer("hello"));
      stdin.close();
      process.exitHandler(code -> {
        context.assertEquals(0, status.getAndIncrement());
        async.complete();
      });
    });
  }

  @Test
  public void testStdout(TestContext context) {
    testStream(context, Arrays.asList("/usr/bin/java", "-cp", "target/test-classes", "EchoStdout", "the_echoed_value"), ChildProcess::stdout);
  }

  @Test
  public void testStderr(TestContext context) {
    testStream(context, Arrays.asList("/usr/bin/java", "-cp", "target/test-classes", "EchoStderr", "the_echoed_value"), ChildProcess::stderr);
  }

  private void testStream(TestContext testContext, List<String> cmd, Function<ChildProcess, ProcessReadStream> streamExtractor) {
    Async async = testContext.async();
    AtomicInteger status = new AtomicInteger();
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      ChildProcess.spawn(vertx, cmd, process -> {
        ProcessReadStream stream = streamExtractor.apply(process);
        stream.handler(buf -> {
          testContext.assertEquals(context, Vertx.currentContext());
          testContext.assertEquals(0, status.getAndIncrement());
          testContext.assertEquals("the_echoed_value", buf.toString());
        });
        stream.endHandler(d -> {
          testContext.assertEquals(context, Vertx.currentContext());
          testContext.assertEquals(1, status.getAndIncrement());
        });
        process.exitHandler(code -> {
          testContext.assertEquals(2, status.getAndIncrement());
          async.complete();
        });
      });
    });
  }


  @Test
  public void testDrainStdin(TestContext testContext) throws IOException {
    Path tmp = Files.createTempFile("test", ".shared");
    tmp.toFile().deleteOnExit();
    Async async = testContext.async();
    AtomicInteger status = new AtomicInteger();
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      ChildProcess.spawn(vertx, Arrays.asList("/usr/bin/java", /*"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",*/ "-cp", "target/test-classes", "io.vertx.ext.childprocess.Main", tmp.toFile().getAbsolutePath()), process -> {
        ProcessWriteStream stdin = process.stdin();
        stdin.setWriteQueueMaxSize(8000);
        process.exitHandler(code -> {
          testContext.assertEquals(1, status.getAndIncrement());
          testContext.assertEquals(0, code);
          async.complete();
        });
        int size = 0;
        while (!stdin.writeQueueFull()) {
          Buffer buf = Buffer.buffer("hello");
          stdin.write(buf);
          size += buf.length();
        }
        testContext.assertTrue(size > 8000);
        try {
          Files.write(tmp, new byte[1]);
        } catch (IOException e) {
          testContext.fail(e);
          return;
        }
        stdin.drainHandler(v2 -> {
          testContext.assertEquals(context, Vertx.currentContext());
          testContext.assertEquals(0, status.getAndIncrement());
          stdin.write(Buffer.buffer(new byte[]{4})); // EOF
        });
      });
    });
  }

  @Test
  public void testCat(TestContext context) {
    Async async = context.async();
    AtomicInteger status = new AtomicInteger();
    ChildProcess.spawn(vertx, Arrays.asList("/bin/cat"), process -> {
      Buffer out = Buffer.buffer();
      process.stdout().handler(out::appendBuffer);
      ProcessWriteStream stdin = process.stdin();
      stdin.write(Buffer.buffer("hello"));
      stdin.close();
      process.exitHandler(code -> {
        context.assertEquals("hello", out.toString());
        context.assertEquals(0, status.getAndIncrement());
        async.complete();
      });
    });
  }

  @Test
  public void testCwd(TestContext context) {
    Async async = context.async();
    String cwd = new File(new File("target"), "test-classes").getAbsolutePath();
    ChildProcess.spawn(vertx, Arrays.asList("/usr/bin/java", "PrintCwd"), new ProcessOptions().setCwd(cwd), process -> {
      Buffer out = Buffer.buffer();
      process.stdout().handler(out::appendBuffer);
      process.exitHandler(code -> {
        context.assertEquals(0, code);
        context.assertEquals(cwd, new File(out.toString()).getAbsolutePath());
        async.complete();
      });
    });
  }

  @Test
  public void testEnv(TestContext context) {
    Async async = context.async();
    ChildProcess.spawn(vertx, Arrays.asList("/usr/bin/java", "-cp", "target/test-classes", "PrintEnv"), new ProcessOptions().setEnv(new JsonObject().put("foo", "foo_value")), process -> {
      Buffer out = Buffer.buffer();
      process.stdout().handler(out::appendBuffer);
      process.exitHandler(code -> {
        context.assertEquals(0, code);
        context.assertEquals("foo_value", out.toString());
        async.complete();
      });
    });
  }

  @Test
  public void testStdoutLongSequence(TestContext context) {
    Async async = context.async();
    ChildProcess.spawn(vertx, Arrays.asList("/usr/bin/java", "-cp", "target/test-classes", "StdoutLongSequence"), process -> {
      Buffer out = Buffer.buffer();
      process.stdout().handler(out::appendBuffer);
      process.exitHandler(code -> {
        context.assertEquals(0, code);
        StringBuilder expected = new StringBuilder();
        for (int i = 0;i < 100000;i++) {
          expected.append(i);
        }
        context.assertEquals(expected.toString(), out.toString());
        async.complete();
      });
    });
  }

  @Test
  public void testPauseResumeStdout(TestContext testContext) throws IOException {
    Buffer out = Buffer.buffer();
    Async async1 = testContext.async();
    Context context = vertx.getOrCreateContext();
    AtomicReference<ChildProcess> processRef = new AtomicReference<>();
    context.runOnContext(v -> {
      AtomicBoolean paused = new AtomicBoolean();
      ChildProcess.spawn(vertx, Arrays.asList("/usr/bin/java", "-cp", "target/test-classes", "StdoutLongSequence"), process -> {
        processRef.set(process);
        process.stdout().handler(buf -> {
          out.appendBuffer(buf);
          if (out.length() > 1000 && paused.compareAndSet(false, true)) {
            System.out.println("pausing");
            process.stdout().pause();
            vertx.setTimer(1000, id -> {
              async1.complete();
            });
          }
        });
      });
    });
    async1.awaitSuccess(10000);
    System.out.println("resuming");
    Async async2 = testContext.async();
    context.runOnContext(v -> {
      ChildProcess process = processRef.get();
      process.exitHandler(code -> {
        System.out.println("exit");
        testContext.assertEquals(0, code);
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
          expected.append(i);
        }
        testContext.assertEquals(expected.toString(), out.toString());
        async2.complete();
      });
      process.stdout().handler(buf -> {
        System.out.println("buffer after resume " + buf.length());
        out.appendBuffer(buf);
      });
      process.stdout().resume();
    });
  }
}
