package de.bonndan.nivio.output;

import de.bonndan.nivio.model.Item;
import de.bonndan.nivio.model.Label;
import de.bonndan.nivio.output.icons.Icons;
import de.bonndan.nivio.output.icons.VendorIcons;
import de.bonndan.nivio.util.URLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Optional;

import static de.bonndan.nivio.output.icons.Icons.DEFAULT_ICON;

@Service
public class LocalServer implements EnvironmentAware {

    public static final String VENDORICONS_PATH = "/vendoricons";
    public static final String VENDOR_PREFIX = "vendor://";
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalServer.class);
    private static Environment env;
    private final VendorIcons vendorIcons;
    private final URL defaultIcon;
    /**
     * without slash
     */
    private final String baseUrl;
    private String imageProxy;

    public LocalServer(@Value("${nivio.baseUrl:}") String baseUrl, VendorIcons vendorIcons) {
        if (!StringUtils.isEmpty(baseUrl)) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        } else {
            this.baseUrl = "http://" + host() + ":" + port();
        }
        this.vendorIcons = vendorIcons;
        defaultIcon = getUrl(DEFAULT_ICON.getName());
    }

    /**
     * Turns the base 64 encode vendor icon url back into the orginal icon url.
     *
     * @param requestURI base64 encoded request including nivio path
     * @return string icon url
     */
    public static String deproxyUrl(String requestURI) {
        String iconRequestURI = requestURI.split(VENDORICONS_PATH)[1];
        iconRequestURI = StringUtils.trimLeadingCharacter(iconRequestURI, '/');

        iconRequestURI = new String(Base64.getUrlDecoder().decode(iconRequestURI));

        return iconRequestURI;
    }

    private static String host() {
        return InetAddress.getLoopbackAddress().getHostName();
    }

    private static String port() {

        if (env != null) {
            String port = env.getProperty("local.server.port");
            if (port != null && Integer.parseInt(port) != 0) {
                return port;
            }
        }

        return "8080";
    }

    /**
     * Returns the visible url.
     *
     * @param path path to add
     * @return url with host, port
     */
    public URL getUrl(String path) {
        try {
            return new URL(baseUrl + (path.startsWith("/") ? path : "/" + path));
        } catch (MalformedURLException ignored) {
            LOGGER.warn("Failed to build url for {}", path);
            return defaultIcon;
        }
    }

    /**
     * Returns the visible url.
     *
     * @param parts path to add, concatenated by "/"
     * @return url with host, port
     */
    public URL getUrl(String... parts) {
        return getUrl(StringUtils.arrayToDelimitedString(parts, "/"));
    }

    public URL getIconUrl(Item item) {

        String icon = item.getIcon();
        if (!StringUtils.isEmpty(icon)) {

            if (icon.startsWith(VENDOR_PREFIX)) {
                String key = icon.replace(VENDOR_PREFIX, "").toLowerCase();
                return vendorIcons.getUrl(key).map(this::proxiedUrl).orElse(defaultIcon);
            }

            URL iconUrl = getIconUrl(icon);
            return iconUrl != null ? iconUrl : getUrl(icon);
        }

        String type = item.getLabel(Label.type);
        if (StringUtils.isEmpty(type)) {
            return getIconUrl(DEFAULT_ICON.getName());
        }

        //fallback to item.type
        Icons ic = Icons.of(type.toLowerCase()).orElse(DEFAULT_ICON);
        return getIconUrl(ic.getName());
    }

    /**
     * Provides an URL for a locally served icon.
     */
    private URL getIconUrl(String icon) {
        URL url = URLHelper.getURL(icon).orElse(null);

        //local icon urls are not supported
        if (url != null && URLHelper.isLocal(url)) {
            url = null;
        }

        if (url == null) {
            return getUrl("/icons/" + icon + ".png");
        }

        return proxiedUrl(url);
    }

    private URL proxiedUrl(URL url) {

        if (imageProxy == null) {
            URL imageProxy = getUrl(VENDORICONS_PATH);
            setImageProxy(imageProxy.toString());
        }

        if (!StringUtils.isEmpty(imageProxy)) {
            try {
                return new URL(imageProxy + "/" + Base64.getUrlEncoder().encodeToString(url.toString().getBytes()));
            } catch (MalformedURLException e) {
                LOGGER.error("Failed to build image proxy url from {}", imageProxy + "/" + url.getPath());
            }
        }
        return url;
    }

    public void setImageProxy(String imageProxy) {
        this.imageProxy = imageProxy;
    }

    @Override
    public void setEnvironment(Environment environment) {
        env = environment;
    }
}
