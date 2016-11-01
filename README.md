# prototype

Was making a simpler version of an existing game called Factorio.
Unfortunately, after about 100 conveyor belts were created in the game, the JVM's garbage collector started taking waaaaaaaay too long.
Non-GC performance wasn't great either, the frames per second would drop to around 22, meaning it was taking longer than 16ms (30 fps) to process all the game data. I used Visual VM to profile the prototype, and the slowest part was updating the game data in the entity component system. Basically, updating nested maps a bunch of times using assoc-in. I wasn't sure how to proceed, other than reducing scope, so I just stopped.

Reused the animation system from my "animation" repo.

Read more about entity component systems and created a faster one. The basic idea is that systems are functions that have predicate functions to determine which entities it will operate on. Every time an entity is added, all of the system's predicate functions run. If a predicate returns true, the entity's ID is 'added' to the system function. When the ecs is updated, the ecs knows which entities to pass to each system. 

This is an improvement over the old method, which was basically to pass all of the entities to every system function and filter out the relevant entities on every frame.

My programmer art, in gif form : 
![alt text](http://i.imgur.com/8wsZS6U.gif "Oops.")

To describe the picture: 

The perspective is top down.

At the bottom is a bunch of iron ore with a giant drill on top of it. The drill is depositing pieces of iron ore onto the conveyor belt above it. The conveyor belt takes the pieces of ore up to some swinging arms. The swinging arms are picking up the iron ore and depositing them into factories.

The factories, when the user clicks on them, have a crude UI that allows the production of bullets. 

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
