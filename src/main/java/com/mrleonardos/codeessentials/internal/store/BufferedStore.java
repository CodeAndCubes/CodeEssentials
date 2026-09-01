package com.mrleonardos.codeessentials.internal.store;

import com.mrleonardos.codeessentials.api.store.StoreResult;

public interface BufferedStore {

    boolean unsaved();

    StoreResult flush();
}
