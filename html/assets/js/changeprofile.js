var state_changeprofile = {
  preload: function () {
    game.add.sprite(0, 0, 'background');
    game.add.bitmapText(280, 130, 'chiller', 'Change Profile', 70);
    game.add.bitmapText(260, 230, 'chiller', 'Nickname', 54);
    game.add.bitmapText(280, 290, 'chiller', 'Gender', 54);
    game.add.bitmapText(435, 290, 'chiller', 'Boy', 54);
    game.add.bitmapText(530, 290, 'chiller', 'Girl', 54);
    game.add.bitmapText(290, 345, 'chiller', 'Avatar', 54);

    game.add.button(325, 410, 'button', do_changepf, this, 1, 0, 2, 0);
    game.add.bitmapText(340, 415, 'chiller', 'Confirm', 28);



    game.add.button(465, 410, 'button', do_backloginpf, this, 1, 0, 2, 0);
    game.add.bitmapText(490, 415, 'chiller', 'Back', 28);

    game.avatar = game.add.sprite(10, 250, 'figure1');
    show("changeprofile");
  }
}

function do_backloginpf(){
  game.state.start('login');
}

function update_avatar_changeprofile() {
  game.avatar.kill();
  game.avatar = game.add.sprite(10, 250, $("#changeprofile_avatar").val());
}

function do_changepf() {
  var nickname = $("#changeprofile_nickname").val();
  var avatar =  $("#changeprofile_avatar").val();
  var gender = parseInt($("input[name='gender']:checked").val());
  var req = {
    'sid':sid,
    'nickname': nickname,
    'avatar': avatar,
    'gender': gender
  };

  console.log(req);
  var v_nickname = /{};:'/.test(nickname);

  // todo: 判断图像地址
  if (v_nickname) {
    alert("昵称含有非法字符");
    return;
  }

  $.ajax({
    url: "/api/user/changeprofile",
    type: "POST",
    contentType: 'application/json',
    data: JSON.stringify(req),
    success: function (data, status) {
      alert("修改成功");
      console.log(data);
      game.state.start('userinterface'); // 跳转用户界面
    },
    error: function (data, status) {
      alert("修改失败");
      console.log(data);
    }
  });
}