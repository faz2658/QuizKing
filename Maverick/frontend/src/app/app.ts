import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { GameFx } from './core/game-fx';
import { InstallPrompt } from './core/install-prompt';
import { World } from './core/world';
import { Aurora } from './motion/aurora';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Aurora],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly fx = inject(GameFx);
  protected readonly installer = inject(InstallPrompt);
  protected readonly world = inject(World);

  constructor() {
    // heartbeat while signed in: puts the player on the "online now" map
    effect(() => (this.auth.isLoggedIn() ? this.world.start() : this.world.stop()));
  }
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/welcome');
  }
}
