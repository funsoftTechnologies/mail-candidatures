package app;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableCell;
import java.time.LocalDate;

public class DatePickerTableCell<S> extends TableCell<S, LocalDate> {
    private final DatePicker datePicker = new DatePicker();

    public DatePickerTableCell() {
        datePicker.setOnAction(e -> commitEdit(datePicker.getValue()));
        setGraphic(datePicker);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @Override
    protected void updateItem(LocalDate item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setGraphic(null);
        } else {
            datePicker.setValue(item);
            setGraphic(datePicker);
        }
    }
}
