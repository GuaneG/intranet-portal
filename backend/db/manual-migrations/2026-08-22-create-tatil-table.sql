CREATE TABLE IF NOT EXISTS tatil (
                                     tatil_id INT NOT NULL AUTO_INCREMENT,
                                     ad VARCHAR(100) NOT NULL,
    tarih DATE NOT NULL,
    kategori VARCHAR(30) NOT NULL,
    gun_suresi VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tatil_id),
    CONSTRAINT uk_tatil_tarih UNIQUE (tarih)
    );