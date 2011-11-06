(ns avout.atoms.mongo
  (:require [avout.atoms :as atoms]
            [somnium.congomongo :as mongo]
            [avout.locks :as locks])
  (:import (avout.atoms AtomState)))

(deftype MongoAtomState [conn name]
  AtomState
  (getState [this]
    (:value (mongo/with-mongo conn
              (mongo/fetch-one :atoms :where {:name name}))))

  (setState [this new-value]
    (let [data (mongo/with-mongo conn (mongo/fetch-one :atoms :where {:name name}))]
      (mongo/with-mongo conn
        (mongo/update! :atoms data (assoc data :value new-value))))))

(defn mongo-atom
  ([zk-client mongo-conn name init-value & {:keys [validator]}]
     (doto
         (mongo/with-mongo mongo-conn
           (or (mongo/fetch-one :atoms :where {:name name})
               (mongo/insert! :atoms {:name name}))
           (atoms/distributed-atom zk-client name (MongoAtomState. mongo-conn name)))
       (set-validator! validator)
       (.reset init-value))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mongo-atom examples
(comment

  (use 'avout.atoms)
  (use 'avout.atoms.mongo :reload-all)
  (require '[somnium.congomongo :as mongo])
  (require '[zookeeper :as zk])

  (def zk-client (zk/connect "127.0.0.1"))
  (def mongo-conn (mongo/make-connection "statedb" :host "127.0.0.1" :port 27017))

  (def a0 (mongo-atom zk-client mongo-conn "/a0" {:a 1}))
  @a0
  (swap!! a0 assoc :c 3)
  @a0
  (swap!! a0 update-in [:a] inc)
  @a0

  (def a1 (mongo-atom zk-client mongo-conn "/a1" 1 :validator pos?))
  (add-watch a1 :a1 (fn [key ref old-val new-val]
                      (println key ref old-val new-val)))
  @a1
  (swap!! a1 inc)
  @a1
  (swap!! a1 - 2)
  (remove-watch a1 :a1)
  @a1

)
