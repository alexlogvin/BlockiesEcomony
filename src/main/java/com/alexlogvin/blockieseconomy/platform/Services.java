package com.alexlogvin.blockieseconomy.platform;

import java.util.ServiceLoader;

/**
 * Resolves the loader-specific implementations.
 *
 * <p>Each loader jar carries exactly one provider per service, declared in
 * {@code META-INF/services}. A missing provider is a packaging error, so it fails loudly
 * at load rather than producing a confusing NullPointerException later.
 */
public final class Services {

    public static final Platform PLATFORM = load(Platform.class);

    /**
     * The loader's networking, resolved once.
     *
     * <p>Cached rather than loaded on demand because the implementations hold registered
     * handlers. A fresh ServiceLoader instance per call would hand out an empty one that
     * silently drops every packet.
     */
    public static final Networking NETWORKING = load(Networking.class);

    private Services() {
    }

    public static <T> T load(Class<T> type) {
        ServiceLoader<T> loader = ServiceLoader.load(type);
        T found = null;
        for (T candidate : loader) {
            if (found != null) {
                throw new IllegalStateException(
                        "multiple providers for " + type.getName()
                                + "; exactly one loader implementation must be present");
            }
            found = candidate;
        }
        if (found == null) {
            throw new IllegalStateException(
                    "no provider for " + type.getName()
                            + "; the loader-specific source set was not packaged");
        }
        return found;
    }
}
