package de.bonndan.nivio.output;

import de.bonndan.nivio.output.layout.LayoutedComponent;

import java.io.File;
import java.io.IOException;

/**
 * Any class that can transform a landscape into some sort of output.
 *
 * @param <T> output type
 */
public interface Renderer<T> {

    T render(LayoutedComponent landscape);

    void render(LayoutedComponent landscape, File file) throws IOException;
}
