module com.ltnc {
    requires transitive javafx.controls;
    requires transitive javafx.web; // 🔥 THÊM DÒNG NÀY
    requires javafx.fxml;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires jdk.jsobject;
    requires org.json;

    opens com.ltnc;
    opens com.ltnc.controller to javafx.fxml, javafx.web;

    exports com.ltnc;
    exports com.ltnc.controller;
}
