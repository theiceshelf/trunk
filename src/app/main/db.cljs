(ns app.main.db
  (:require ["sqlite3" :as sqlite]
            ["fs" :as fs]
            [app.shared.util :as util]
            [clojure.string :as str]))


(def db-path "./trunk.db")
(def db (sqlite/Database. db-path))

(defn db-del!  [] (.unlinkSync fs db-path))
(defn db-wipe! [] (.exec db "DELETE FROM words; DELETE FROM articles;" #(println %)))


(def db-seed "

  CREATE TABLE IF NOT EXISTS words (
    word_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    comfort INTEGER,
    translation TEXT
  );

  CREATE TABLE IF NOT EXISTS articles (
    article_id INTEGER PRIMARY KEY,
    name TEXT,
    source TEXT,
    original TEXT,
    word_ids TEXT
  );

")

(defn articles-get
  [callback]
  (let [sql "SELECT * FROM articles"]
    (.all db sql (fn [err rows]
                   (callback rows)))))


(defn- insert-article
  "Takes a word string and creates a new article entry for it."
  [word-str cb]
  (let [
        sql-get-all-words "SELECT * FROM words"
        words             (str/split word-str " ") ;; FIXME: better lowercase and trim all words.
        vals              (apply array [word-str])
        word-ids          (atom [])]

    ;; -- [SQL] ALL words
    (.all db sql-get-all-words                               ;; PERF: this could get slow
          (fn [err rows]
            (let [rows (js->clj rows :keywordize-keys true)] ;; PERF: this could get also get slow
              (doseq [row  rows
                      :let [{:keys [word_id name]} row]]
                (when (some #{name} words)                   ;; PERF this could also be slow.
                  (swap! word-ids conj word_id)))

              ;; -- [SQL] Insert - New article
              ;; now that we have our ids for delimiting words... let's insert it.
              (let [sql-new-article (str "INSERT INTO articles(original, word_ids) VALUES (?, ?)")
                    delimited-ids   (str/join "$" @word-ids)
                    vals            (apply array [word-str delimited-ids])]
                (.run db sql-new-article vals cb)))))))

(defn- insert-words
  "Takes a string representing a new article, and breaks it into chunks.
  Then insert ALL words into the `words` table, if they don't already exist.
  "

  [word-str cb]
  (let [words       (str/split word-str " " )
        placeholder (util/seq->sql-placeholder words)
        vals        (apply array words)
        queryWords  (str "INSERT OR IGNORE INTO words(name) VALUES " placeholder)]
    (.run db queryWords vals cb)))

(defn article-create
  [word-str cb]
  (insert-words word-str
                (fn [err]
                  ;; TODO error handling.
                  (insert-article word-str cb)))

  )

(defn init
  []
  (.exec db db-seed
        (fn [err]
          (when err
            (throw (js/Error. (str "Failed db" err)))))))


;; FIXME: when do I run "db.close()"?
