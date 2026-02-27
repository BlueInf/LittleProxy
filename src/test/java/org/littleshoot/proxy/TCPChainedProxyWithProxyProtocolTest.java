package org.littleshoot.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.codec.http.HttpRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
public final class TCPChainedProxyWithProxyProtocolTest extends BaseChainedProxyTest {

  private final AtomicReference<InetSocketAddress> clientAddressSeenByUpstream =
      new AtomicReference<>();
  private final AtomicReference<InetSocketAddress> clientAddressSeenByDownstream =
      new AtomicReference<>();

  @Override
  protected void setUp() throws IOException {
    clientAddressSeenByUpstream.set(null);
    clientAddressSeenByDownstream.set(null);
    super.setUp();
    proxyServer.abort();
    proxyServer =
        bootstrapProxy()
            .withSendProxyProtocol(true)
            .withName("Downstream")
            .withPort(0)
            .withChainProxyManager(chainedProxyManager())
            .plusActivityTracker(DOWNSTREAM_TRACKER)
            .plusActivityTracker(
                new ActivityTrackerAdapter() {
                  @Override
                  public void requestReceivedFromClient(
                      FlowContext flowContext, HttpRequest httpRequest) {
                    clientAddressSeenByDownstream.compareAndSet(
                        null, flowContext.getClientAddress());
                  }
                })
            .start();
  }

  @Override
  protected HttpProxyServerBootstrap upstreamProxy() {
    return super.upstreamProxy()
        .withAcceptProxyProtocol(true)
        .plusActivityTracker(
            new ActivityTrackerAdapter() {
              @Override
              public void requestReceivedFromClient(
                  FlowContext flowContext, HttpRequest httpRequest) {
                clientAddressSeenByUpstream.compareAndSet(
                    null, flowContext.getClientAddress());
              }
            });
  }

  @Test
  public void testProxyProtocolPreservesClientAddress() {
    compareProxiedAndUnproxiedGET(webHost, DEFAULT_RESOURCE);

    InetSocketAddress downstreamClient = clientAddressSeenByDownstream.get();
    InetSocketAddress upstreamClient = clientAddressSeenByUpstream.get();

    assertThat(downstreamClient)
        .as("Downstream proxy should have seen a client address")
        .isNotNull();
    assertThat(upstreamClient)
        .as("Upstream proxy should have received a client address via proxy protocol")
        .isNotNull();
    assertThat(upstreamClient.getAddress())
        .as("Upstream proxy should see the original client IP via proxy protocol")
        .isEqualTo(downstreamClient.getAddress());
    assertThat(upstreamClient.getPort())
        .as("Upstream proxy should see the original client port via proxy protocol")
        .isEqualTo(downstreamClient.getPort());
  }
}