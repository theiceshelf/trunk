(ns app.main.db
  (:require ["sqlite3" :as sqlite]
            ["fs" :as fs]
            [app.shared.util :as util]
            [clojure.string :as str]))


(def db-path "./trunk.db")
(def db (sqlite/Database. db-path))

(defn db-del!  [] (.unlinkSync fs db-path))
(defn wipe! []
  (println "wip!  called")
  (.exec db "DELETE FROM words; DELETE FROM articles;" #(println %)))


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

(defn- words-get-for-article
  "Looks at a delimited string and queries for all the words in it."
  [article cb]
  (let [word-ids   (article :word_ids)
        words-orig (str/split word-ids "$")]
    (letfn [(recurse [words out]
              (if (= (count out) (count words-orig))
                (cb (clj->js (assoc article :word-data out))) ;; maybe not efficient in the future to use clj->js, could do raw js interop in this fn.
                (let [[x & xs] words]
                  (.get db "SELECT * FROM words WHERE word_id = ?" (array x)
                        (fn [err row]
                          (recurse xs (conj out (js->clj row))))))))]
      (recurse words-orig []))))


(defn article-get
  [id cb]
  (let [query  "SELECT * FROM articles WHERE article_id = ?"
        params (array id)]
    (.get db query params (fn [err row]
                            (words-get-for-article (js->clj row :keywordize-keys true) cb)))))

(defn- get-all-words
  [cb]
  (let [sql "SELECT * FROM words"]
    (.all db sql (fn [err rows] (cb rows)))))

(defn- insert-article
  "Takes a word string and creates a new article entry for it.
  NOTE: This happens before `insert-words`!!
  Welcome to the callback swamp!
  "
  [data cb]
  (let [{:keys [article title source]} data
        words         (util/split-article article)
        word-ids      (atom [])]

    ;; -- [SQL] ALL words
    (get-all-words
          (fn [rows]
            (let [rows (js->clj rows :keywordize-keys true)] ;; PERF: this could get also get slow
              (doseq [row  rows
                      :let [{:keys [word_id name]} row]]
                (when (some #{name} words)             ;; FIXME this aint working?      ;; PERF this could also be slow.
                  (swap! word-ids conj word_id)))

              ;; -- [SQL] Insert - New article
              ;; now that we have our ids for delimiting words... let's insert it.
              (let [sql-new-article (str "INSERT INTO articles(original, word_ids, name, source) VALUES (?, ?, ?, ?)")
                    delimited-ids   (str/join "$" @word-ids)
                    vals            (apply array [article delimited-ids title source])]
                (.run db sql-new-article vals (fn [err]
                                                (this-as this
                                                  (article-get (.-lastID ^js this) cb))))))))))

;; PICK UP: FIXME: when inserting for the first time that the database
;; encounters a word, it will not allow that word to appear later that is, if
;; your enter "foo bar baz! foo bar goobie!" The delimited ids will not capture
;; repeat words and the delimited result, after fetching the respective words
;; will be: "foo bar baz! goobie!"


(defn- insert-words
  "Takes a string representing a new article, and breaks it into chunks.
  Then insert ALL words into the `words` table, if they don't already exist."
  [word-str cb]
  (let [words       (util/split-article word-str)
        _ (prn words)
        placeholder (util/seq->sql-placeholder words)
        vals        (apply array words)
        queryWords  (str "INSERT OR IGNORE INTO words(name) VALUES " placeholder)]
    (.run db queryWords vals cb)))

(defn article-create
  [data cb]
  (insert-words (data :article)
                (fn [err]
                  ;; TODO error handling.
                  (insert-article data cb))))

(defn init
  []
  (.exec db db-seed
        (fn [err]
          (when err
            (throw (js/Error. (str "Failed db" err)))))))


;; FIXME: when do I run "db.close()"?
