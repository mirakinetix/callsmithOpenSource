package com.mirakinetix.callsmith.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.SVGPath;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class IconView extends Group {

    private static final String RESOURCE_PREFIX =
            "/icons/";

    private static final double DEFAULT_SIZE = 16.0;

    private static final Color DEFAULT_COLOR =
            Color.web("#374151");

    private final double size;

    private Color iconColor =
            DEFAULT_COLOR;

    public IconView(Icon icon) {
        this(icon, DEFAULT_SIZE);
    }

    public IconView(
            Icon icon,
            double size
    ) {
        this.size = size;

        loadIcon(icon);
    }

    public void setIconColor(
            Color color
    ) {
        if (color == null) {
            throw new IllegalArgumentException(
                    "Icon color cannot be null"
            );
        }

        this.iconColor = color;

        getChildren().forEach(
                this::applyColor
        );
    }

    private void loadIcon(
            Icon icon
    ) {
        String resourceName =
                RESOURCE_PREFIX
                        + icon.resourceName()
                        + ".svg";

        try (InputStream inputStream =
                     IconView.class.getResourceAsStream(
                             resourceName
                     )) {

            if (inputStream == null) {
                throw new IllegalArgumentException(
                        "Icon resource not found: "
                                + resourceName
                );
            }

            Document document =
                    DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(inputStream);

            Element root =
                    document.getDocumentElement();

            double viewBoxWidth =
                    readViewBoxDimension(
                            root,
                            2
                    );

            double viewBoxHeight =
                    readViewBoxDimension(
                            root,
                            3
                    );

            List<Node> nodes =
                    parseShapes(root);

            getChildren().addAll(nodes);

            double scaleX =
                    size / viewBoxWidth;

            double scaleY =
                    size / viewBoxHeight;

            setScaleX(scaleX);
            setScaleY(scaleY);

            getChildren().forEach(
                    this::applyColor
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to load icon: "
                            + resourceName,
                    exception
            );
        }
    }

    private List<Node> parseShapes(
            Element root
    ) {
        List<Node> nodes =
                new ArrayList<>();

        NodeList pathElements =
                root.getElementsByTagName("path");

        for (int index = 0;
             index < pathElements.getLength();
             index++) {

            Element element =
                    (Element) pathElements.item(index);

            String pathData =
                    element.getAttribute("d");

            if (pathData.isBlank()) {
                continue;
            }

            SVGPath path =
                    new SVGPath();

            path.setContent(pathData);

            configureStroke(path, element);

            nodes.add(path);
        }

        NodeList circleElements =
                root.getElementsByTagName("circle");

        for (int index = 0;
             index < circleElements.getLength();
             index++) {

            Element element =
                    (Element) circleElements.item(index);

            Circle circle =
                    new Circle(
                            readDouble(
                                    element,
                                    "cx"
                            ),
                            readDouble(
                                    element,
                                    "cy"
                            ),
                            readDouble(
                                    element,
                                    "r"
                            )
                    );

            configureStroke(circle, element);

            nodes.add(circle);
        }

        NodeList lineElements =
                root.getElementsByTagName("line");

        for (int index = 0;
             index < lineElements.getLength();
             index++) {

            Element element =
                    (Element) lineElements.item(index);

            Line line =
                    new Line(
                            readDouble(
                                    element,
                                    "x1"
                            ),
                            readDouble(
                                    element,
                                    "y1"
                            ),
                            readDouble(
                                    element,
                                    "x2"
                            ),
                            readDouble(
                                    element,
                                    "y2"
                            )
                    );

            configureStroke(line, element);

            nodes.add(line);
        }

        NodeList polylineElements =
                root.getElementsByTagName("polyline");

        for (int index = 0;
             index < polylineElements.getLength();
             index++) {

            Element element =
                    (Element) polylineElements.item(index);

            Polyline polyline =
                    new Polyline(
                            parsePoints(
                                    element.getAttribute(
                                            "points"
                                    )
                            )
                    );

            configureStroke(
                    polyline,
                    element
            );

            nodes.add(polyline);
        }

        NodeList polygonElements =
                root.getElementsByTagName("polygon");

        for (int index = 0;
             index < polygonElements.getLength();
             index++) {

            Element element =
                    (Element) polygonElements.item(index);

            Polygon polygon =
                    new Polygon(
                            parsePoints(
                                    element.getAttribute(
                                            "points"
                                    )
                            )
                    );

            configureStroke(
                    polygon,
                    element
            );

            nodes.add(polygon);
        }

        NodeList rectangleElements =
                root.getElementsByTagName("rect");

        for (int index = 0;
             index < rectangleElements.getLength();
             index++) {

            Element element =
                    (Element) rectangleElements.item(index);

            Rectangle rectangle =
                    new Rectangle(
                            readDouble(
                                    element,
                                    "x"
                            ),
                            readDouble(
                                    element,
                                    "y"
                            ),
                            readDouble(
                                    element,
                                    "width"
                            ),
                            readDouble(
                                    element,
                                    "height"
                            )
                    );

            configureStroke(
                    rectangle,
                    element
            );

            nodes.add(rectangle);
        }

        return nodes;
    }

    private void configureStroke(
            Shape shape,
            Element element
    ) {
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(iconColor);

        String strokeWidth =
                element.getAttribute(
                        "stroke-width"
                );

        if (!strokeWidth.isBlank()) {
            shape.setStrokeWidth(
                    Double.parseDouble(
                            strokeWidth
                    )
            );
        } else {
            shape.setStrokeWidth(2.0);
        }

        shape.setStrokeLineCap(
                javafx.scene.shape.StrokeLineCap.ROUND
        );

        shape.setStrokeLineJoin(
                javafx.scene.shape.StrokeLineJoin.ROUND
        );
    }

    private void applyColor(
            Node node
    ) {
        if (node instanceof Shape shape) {
            shape.setStroke(iconColor);
        }
    }

    private double[] parsePoints(
            String points
    ) {
        String normalized =
                points.replace(
                        ",",
                        " "
                ).trim();

        if (normalized.isEmpty()) {
            return new double[0];
        }

        String[] values =
                normalized.split(
                        "\\s+"
                );

        double[] coordinates =
                new double[values.length];

        for (int index = 0;
             index < values.length;
             index++) {

            coordinates[index] =
                    Double.parseDouble(
                            values[index]
                    );
        }

        return coordinates;
    }

    private double readDouble(
            Element element,
            String attribute
    ) {
        String value =
                element.getAttribute(
                        attribute
                );

        if (value.isBlank()) {
            return 0.0;
        }

        return Double.parseDouble(value);
    }

    private double readViewBoxDimension(
            Element root,
            int index
    ) {
        String viewBox =
                root.getAttribute(
                        "viewBox"
                );

        if (viewBox.isBlank()) {
            return 24.0;
        }

        String[] values =
                viewBox.trim().split(
                        "\\s+"
                );

        if (values.length != 4) {
            return 24.0;
        }

        return Double.parseDouble(
                values[index]
        );
    }
}