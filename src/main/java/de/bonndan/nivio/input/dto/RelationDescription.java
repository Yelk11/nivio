package de.bonndan.nivio.input.dto;

import de.bonndan.nivio.model.LandscapeItem;
import de.bonndan.nivio.model.RelationItem;
import de.bonndan.nivio.model.RelationType;

public class RelationDescription implements RelationItem<String> {

    private RelationType type;
    private String description;
    private String format;
    private String source;
    private String target;

    public RelationDescription(){}

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTarget() {
        return target;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public RelationType getType() {
        return type;
    }

    public void setType(RelationType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "RelationDescription{" +
                "type=" + type +
                ", description='" + description + '\'' +
                ", format='" + format + '\'' +
                ", source='" + source + '\'' +
                ", target='" + target + '\'' +
                '}';
    }
}
