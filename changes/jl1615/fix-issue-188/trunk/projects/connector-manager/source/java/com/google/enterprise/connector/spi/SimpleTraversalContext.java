// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.spi;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Simple implementation of the {@link TraversalContext} interface.
 * Connector developers may want to use this to implement unit tests.
 *
 * @since 2.4
 */
public class SimpleTraversalContext implements TraversalContext {
  private long maxDocumentSize = Long.MAX_VALUE;
  private Set<String> mimeTypeSet = null;
  private long traversalTimeLimitSeconds = 30 * 60;

  public synchronized void setMaxDocumentSize(long maxDocumentSize) {
    validateArgument("maxDocumentSize", maxDocumentSize);
    this.maxDocumentSize = maxDocumentSize;
  }

  public synchronized void setMimeTypeSet(Set<String> mimeTypeSet) {
    this.mimeTypeSet = mimeTypeSet;
  }

  public synchronized void setTraversalTimeLimitSeconds(long limit) {
    validateArgument("traversalTimeLimitSeconds", limit);
    this.traversalTimeLimitSeconds = limit;
  }

  private void validateArgument(String property, long value) {
    if (value < 0) {
      throw new IllegalArgumentException(
          "Illegal value for " + property + ": " + value);
    }
  }

  public synchronized long maxDocumentSize() {
    return maxDocumentSize;
  }

  public synchronized int mimeTypeSupportLevel(String mimeType) {
    return (mimeTypeSet == null || mimeTypeSet.contains(mimeType)) ? 1 : 0;
  }

  public synchronized String preferredMimeType(Set<String> mimeTypes) {
    Set<String> choices = new HashSet<String>(mimeTypes);
    if (mimeTypeSet != null) {
      choices.retainAll(mimeTypeSet);
    }
    Iterator<String> it = choices.iterator();
    return (it.hasNext()) ? it.next() : null;
  }

  public synchronized long traversalTimeLimitSeconds() {
    return traversalTimeLimitSeconds;
  }
}
