CREATE TABLE "FSOR01_3S".log (
  log_id BIGSERIAL,
  file_name VARCHAR(255) NOT NULL,
  receipt_timestamp TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT now() NOT NULL,
  client_id VARCHAR(36),
  message_id VARCHAR(36),
  send_timestamp TIMESTAMP(0) WITHOUT TIME ZONE,
  response_id VARCHAR(36),
  response_timestamp TIMESTAMP(0) WITHOUT TIME ZONE,
  processing_timestamp TIMESTAMP(0) WITHOUT TIME ZONE,
  timeout TIMESTAMP(0) WITHOUT TIME ZONE,
  msg_index BIGINT,
  status VARCHAR(250),
  err_source VARCHAR(20),
  err_code VARCHAR,
  err_description VARCHAR,
  vs_name VARCHAR(100),
  keywords VARCHAR,
  CONSTRAINT log_pkey PRIMARY KEY(log_id)
)
WITH (oids = false);

COMMENT ON COLUMN "FSOR01_3S".log.log_id
IS 'Идентификатор записи лога';

COMMENT ON COLUMN "FSOR01_3S".log.file_name
IS 'Уникальное имя исходного файла, полученного от ИС АП';

COMMENT ON COLUMN "FSOR01_3S".log.receipt_timestamp
IS 'Штамп времени получения исходного файла от ИС АП';

COMMENT ON COLUMN "FSOR01_3S".log.client_id
IS 'Клиентский идентификатор, присвоенный запросу ИС АП';

COMMENT ON COLUMN "FSOR01_3S".log.message_id
IS 'Идентификатор запроса в СМЭВ, присвоенный запросу ИС АП СМЭВ-адаптером';

COMMENT ON COLUMN "FSOR01_3S".log.send_timestamp
IS 'Штамп времени отправки запроса в СМЭВ';

COMMENT ON COLUMN "FSOR01_3S".log.response_id
IS 'Идентификатор ответа от СМЭВ';

COMMENT ON COLUMN "FSOR01_3S".log.response_timestamp
IS 'Штамп времени получения ответа СМЭВ';

COMMENT ON COLUMN "FSOR01_3S".log.processing_timestamp
IS 'Штамп времени обработки и выгрузки ответа СМЭВ в каталог ИС АП';

COMMENT ON COLUMN "FSOR01_3S".log.timeout
IS 'Время окончания ожидания ответа на запрос';

COMMENT ON COLUMN "FSOR01_3S".log.msg_index
IS 'Порядковый номер запроса за сутки в пределах одного вида сведений';

COMMENT ON COLUMN "FSOR01_3S".log.status
IS 'Текущий статус запроса';

COMMENT ON COLUMN "FSOR01_3S".log.err_source
IS 'Система-источник сообщения об ошибке';

COMMENT ON COLUMN "FSOR01_3S".log.err_code
IS 'Код ошибки';

COMMENT ON COLUMN "FSOR01_3S".log.err_description
IS 'Текст сообщения об ошибке';

COMMENT ON COLUMN "FSOR01_3S".log.vs_name
IS 'Краткое наименование вида сведений для отображения в системе визуализации';

COMMENT ON COLUMN "FSOR01_3S".log.keywords
IS 'Набор ключевых атрибутов запроса, разделенных точкой с запятой';

CREATE INDEX file_idx ON "FSOR01_3S".log
  USING btree (file_name COLLATE pg_catalog."default");

CREATE INDEX status_idx ON "FSOR01_3S".log
  USING btree (status COLLATE pg_catalog."default");


ALTER TABLE "FSOR01_3S".log
  OWNER TO smev;


CREATE TABLE "FSOR01_3S".msg_counter (
  session_date DATE NOT NULL,
  vs_namespace VARCHAR(1024) NOT NULL,
  msg_count INTEGER DEFAULT 0,
  CONSTRAINT msg_counter_pkey PRIMARY KEY(session_date, vs_namespace)
)
WITH (oids = false);

COMMENT ON COLUMN "FSOR01_3S".msg_counter.session_date
IS 'Дата, за которую подсчитывается количество запросов в СМЭВ';

COMMENT ON COLUMN "FSOR01_3S".msg_counter.vs_namespace
IS 'Пространство имен схемы вида сведений (идентификатор вида сведений)';

COMMENT ON COLUMN "FSOR01_3S".msg_counter.msg_count
IS 'Общее количество запросов данного вида сведений, отправленных за данную дату';


ALTER TABLE "FSOR01_3S".msg_counter
  OWNER TO smev;


CREATE TABLE "FSOR01_3S".timestamps (
  request_timestamp TIMESTAMP WITHOUT TIME ZONE,
  response_timestamp TIMESTAMP WITHOUT TIME ZONE
)
WITH (oids = false);

COMMENT ON TABLE "FSOR01_3S".timestamps
IS 'Таблица для промежуточного хранения максимальных штампов времени обработки запросов и ответов';

COMMENT ON COLUMN "FSOR01_3S".timestamps.request_timestamp
IS 'Штамп времени создания последнего обработанного адаптером запроса';

COMMENT ON COLUMN "FSOR01_3S".timestamps.response_timestamp
IS 'Штамп времени обработки последнего обработанного адаптером ответа';


ALTER TABLE "FSOR01_3S".timestamps
  OWNER TO smev;

