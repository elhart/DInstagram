# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table app_logger (
  id                        bigint auto_increment not null,
  app_name                  varchar(255),
  event                     varchar(255),
  timestamp                 varchar(255),
  content                   varchar(255),
  display_id                varchar(255),
  constraint pk_app_logger primary key (id))
;

create table local_info (
  id                        bigint auto_increment not null,
  url_path                  varchar(255),
  number_of_images          bigint,
  constraint pk_local_info primary key (id))
;

create table mimages (
  id                        bigint auto_increment not null,
  id_instagram              varchar(255),
  url                       varchar(255),
  source                    varchar(255),
  author_name               varchar(255),
  author_pic                varchar(255),
  time_created              varchar(255),
  number_of_likes_ins       bigint,
  number_of_likes_locall    bigint,
  constraint pk_mimages primary key (id))
;

create table tag_info (
  id                        bigint auto_increment not null,
  tag_name                  varchar(255),
  number_of_images          bigint,
  constraint pk_tag_info primary key (id))
;




# --- !Downs

SET FOREIGN_KEY_CHECKS=0;

drop table app_logger;

drop table local_info;

drop table mimages;

drop table tag_info;

SET FOREIGN_KEY_CHECKS=1;

