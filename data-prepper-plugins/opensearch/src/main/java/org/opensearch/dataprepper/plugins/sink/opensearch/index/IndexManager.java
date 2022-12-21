package org.opensearch.dataprepper.plugins.sink.opensearch.index;

import java.io.IOException;

public interface IndexManager{
	void setupIndex() throws IOException;
	String getIndexName(final String indexAlias) throws IOException;
}
