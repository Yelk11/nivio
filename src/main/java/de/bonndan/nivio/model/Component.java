package de.bonndan.nivio.model;

/**
 * Base for landscapes, groups and items.
 *
 *
 */
public interface Component {

    /**
     * Returns the landscape-wide unique identifier of a server or application.
     */
    String getIdentifier();

    /**
     * A human readable and/or well known name.
     */
    String getName();

    /**
     * A way to contact to responsible.
     */
    String getContact();

    String getDescription();
}
