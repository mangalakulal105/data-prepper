package org.opensearch.dataprepper.plugins.source.opensearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.dataprepper.plugins.source.opensearch.worker.client.OpenSearchClientFactory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenSearchClientRefresherTest {
    private static final String TEST_USERNAME = "test_user";
    private static final String TEST_PASSWORD = "test_password";

    @Mock
    private OpenSearchClientFactory openSearchClientFactory;

    @Mock
    private OpenSearchSourceConfiguration openSearchSourceConfiguration;

    @Mock
    private OpenSearchClient openSearchClient;

    private OpenSearchClientRefresher createObjectUnderTest() {
        return new OpenSearchClientRefresher(openSearchClientFactory, openSearchSourceConfiguration);
    }

    @BeforeEach
    void setup() {
        when(openSearchClientFactory.provideOpenSearchClient(eq(openSearchSourceConfiguration)))
                .thenReturn(openSearchClient);
    }

    @Test
    void testGet() {
        final OpenSearchClientRefresher objectUnderTest = createObjectUnderTest();
        assertThat(objectUnderTest.get(), equalTo(openSearchClient));
    }

    @Test
    void testGetAfterUpdateWithBasicAuthUnchanged() {
        final OpenSearchClientRefresher objectUnderTest = createObjectUnderTest();
        when(openSearchSourceConfiguration.getUsername()).thenReturn(TEST_USERNAME);
        when(openSearchSourceConfiguration.getPassword()).thenReturn(TEST_PASSWORD);
        final OpenSearchSourceConfiguration newConfig = mock(OpenSearchSourceConfiguration.class);
        when(newConfig.getUsername()).thenReturn(TEST_USERNAME);
        when(newConfig.getPassword()).thenReturn(TEST_PASSWORD);
        objectUnderTest.update(newConfig);
        assertThat(objectUnderTest.get(), equalTo(openSearchClient));
    }

    @Test
    void testGetAfterUpdateWithUsernameChanged() {
        final OpenSearchClientRefresher objectUnderTest = createObjectUnderTest();
        when(openSearchSourceConfiguration.getUsername()).thenReturn(TEST_USERNAME);
        final OpenSearchSourceConfiguration newConfig = mock(OpenSearchSourceConfiguration.class);
        when(newConfig.getUsername()).thenReturn(TEST_USERNAME + "_changed");
        final OpenSearchClient newClient = mock(OpenSearchClient.class);
        when(openSearchClientFactory.provideOpenSearchClient(eq(newConfig)))
                .thenReturn(newClient);
        objectUnderTest.update(newConfig);
        assertThat(objectUnderTest.get(), equalTo(newClient));
    }

    @Test
    void testGetAfterUpdateWithPasswordChanged() {
        final OpenSearchClientRefresher objectUnderTest = createObjectUnderTest();
        when(openSearchSourceConfiguration.getUsername()).thenReturn(TEST_USERNAME);
        when(openSearchSourceConfiguration.getPassword()).thenReturn(TEST_PASSWORD);
        final OpenSearchSourceConfiguration newConfig = mock(OpenSearchSourceConfiguration.class);
        when(newConfig.getUsername()).thenReturn(TEST_USERNAME);
        when(newConfig.getPassword()).thenReturn(TEST_PASSWORD + "_changed");
        final OpenSearchClient newClient = mock(OpenSearchClient.class);
        when(openSearchClientFactory.provideOpenSearchClient(eq(newConfig)))
                .thenReturn(newClient);
        objectUnderTest.update(newConfig);
        assertThat(objectUnderTest.get(), equalTo(newClient));
    }
}